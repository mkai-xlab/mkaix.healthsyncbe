package com.g93.be.service;

import com.g93.be.chat.KnowledgeIndexRequestedEvent;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeIndexingWorker {

    private final KnowledgeIndexStateService stateService;
    private final KnowledgeDocumentOperationCoordinator operationCoordinator;
    private final KnowledgeDocumentReader documentReader;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void index(KnowledgeIndexRequestedEvent event) {
        operationCoordinator.executeExclusively(event.documentId(), () -> {
            indexExclusively(event);
            return null;
        });
    }

    private void indexExclusively(KnowledgeIndexRequestedEvent event) {
        log.info("Starting knowledge indexing for document {}", event.documentId());
        try {
            KnowledgeDocument knowledge = stateService.markProcessing(event.documentId());
            if (knowledge == null) {
                log.info("Skipping indexing because knowledge document {} was deleted", event.documentId());
                return;
            }
            List<Document> parsed = documentReader.read(knowledge);
            List<Document> enriched = parsed.stream()
                    .filter(document -> document != null && document.isText())
                    .map(document -> new Document(document.getText(), metadata(knowledge)))
                    .toList();
            List<Document> chunks = splitter.apply(enriched);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No readable text was found in the document");
            }
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq("sourceKey", knowledge.getSourceKey()).build());
            vectorStore.add(chunks);
            if (!stateService.markIndexed(event.documentId(), chunks.size())) {
                vectorStore.delete(new FilterExpressionBuilder()
                        .eq("sourceKey", knowledge.getSourceKey()).build());
                log.info("Discarded indexed chunks because knowledge document {} was deleted", event.documentId());
                return;
            }
            log.info("Knowledge indexing completed for document {} with {} chunks",
                    event.documentId(), chunks.size());
        } catch (RuntimeException | LinkageError exception) {
            log.error("Knowledge indexing failed for document {}", event.documentId(), exception);
            stateService.markFailedIfPresent(event.documentId(), truncate(exception.getMessage()));
        }
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
        if (document.getAccessScope() == KnowledgeAccessScope.OWNER
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
