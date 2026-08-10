package com.g93.be.service;

import com.g93.be.chat.KnowledgeIndexRequestedEvent;
import com.g93.be.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeIndexingWorker {

    private final KnowledgeIndexStateService stateService;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void index(KnowledgeIndexRequestedEvent event) {
        log.info("Starting knowledge indexing for document {}", event.documentId());
        KnowledgeDocument knowledge = stateService.markProcessing(event.documentId());
        try {
            List<Document> parsed = readDocuments(knowledge);
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
            stateService.markIndexed(event.documentId(), chunks.size());
            log.info("Knowledge indexing completed for document {} with {} chunks",
                    event.documentId(), chunks.size());
        } catch (RuntimeException | LinkageError exception) {
            log.error("Knowledge indexing failed for document {}", event.documentId(), exception);
            stateService.markFailed(event.documentId(), truncate(exception.getMessage()));
        }
    }

    List<Document> readDocuments(KnowledgeDocument knowledge) {
        FileSystemResource resource = new FileSystemResource(knowledge.getStoragePath());
        if (isPdf(knowledge)) {
            return readPdf(resource);
        }
        if (isPlainText(knowledge)) {
            TextReader reader = new TextReader(resource);
            reader.setCharset(StandardCharsets.UTF_8);
            return reader.get();
        }
        return new TikaDocumentReader(resource).get();
    }

    private List<Document> readPdf(FileSystemResource resource) {
        try (PDDocument pdf = PDDocument.load(resource.getInputStream())) {
            String text = new PDFTextStripper().getText(pdf);
            return List.of(new Document(text));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Could not read PDF document", exception);
        }
    }

    private boolean isPdf(KnowledgeDocument knowledge) {
        if ("application/pdf".equalsIgnoreCase(knowledge.getContentType())) {
            return true;
        }
        String originalName = knowledge.getOriginalName();
        return originalName != null && originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    private boolean isPlainText(KnowledgeDocument knowledge) {
        if ("text/plain".equalsIgnoreCase(knowledge.getContentType())) {
            return true;
        }
        String originalName = knowledge.getOriginalName();
        return originalName != null && originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".txt");
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
