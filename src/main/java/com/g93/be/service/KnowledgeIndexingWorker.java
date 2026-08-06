package com.g93.be.service;

import com.g93.be.chat.KnowledgeIndexRequestedEvent;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeIndexingWorker {

    private final KnowledgeDocumentRepository repository;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void index(KnowledgeIndexRequestedEvent event) {
        KnowledgeDocument knowledge = repository.findById(event.documentId())
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
        knowledge.setStatus(KnowledgeDocumentStatus.PROCESSING);
        repository.save(knowledge);
        try {
            List<Document> parsed = new TikaDocumentReader(new FileSystemResource(knowledge.getStoragePath())).get();
            List<Document> enriched = parsed.stream()
                    .filter(Document::isText)
                    .map(document -> new Document(document.getText(), metadata(knowledge)))
                    .toList();
            List<Document> chunks = splitter.apply(enriched);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No readable text was found in the document");
            }
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq("sourceKey", knowledge.getSourceKey()).build());
            vectorStore.add(chunks);
            knowledge.setChunkCount(chunks.size());
            knowledge.setStatus(KnowledgeDocumentStatus.INDEXED);
            knowledge.setIndexedAt(LocalDateTime.now());
            knowledge.setErrorMessage(null);
        } catch (RuntimeException exception) {
            knowledge.setStatus(KnowledgeDocumentStatus.FAILED);
            knowledge.setErrorMessage(truncate(exception.getMessage()));
        }
        repository.save(knowledge);
    }

    private Map<String, Object> metadata(KnowledgeDocument document) {
        String reference = document.getSourceUrl() == null
                ? "knowledge-document:" + document.getId() : document.getSourceUrl();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceKey", document.getSourceKey());
        metadata.put("knowledgeDocumentId", document.getId());
        metadata.put("title", document.getTitle());
        metadata.put("sourceType", document.getSourceType().name());
        metadata.put("reference", reference);
        metadata.put("accessScope", document.getAccessScope().name());
        metadata.put("publicationStatus", "PUBLISHED");
        if (document.getAccessScope() == com.g93.be.entity.KnowledgeAccessScope.OWNER
                && document.getUploadedBy() != null) {
            metadata.put("ownerUserId", document.getUploadedBy().getId());
        }
        return metadata;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Knowledge indexing failed";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
