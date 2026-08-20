package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeDocumentDeletionService {

    private final KnowledgeDocumentRepository repository;
    private final VectorStore vectorStore;
    private final ChatProperties properties;

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
        vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", document.getSourceKey()).build());
        repository.delete(document);
        deleteStoredFile(document);
    }

    private void deleteStoredFile(KnowledgeDocument document) {
        if (document.getStoragePath() == null) {
            return;
        }
        try {
            Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
            Path storedFile = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
            if (!storedFile.startsWith(root)) {
                throw new IllegalStateException("Invalid knowledge storage path");
            }
            Files.deleteIfExists(storedFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete stored knowledge document", exception);
        }
    }
}
