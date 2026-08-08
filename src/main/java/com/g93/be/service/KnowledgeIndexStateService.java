package com.g93.be.service;

import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeIndexStateService {

    private final KnowledgeDocumentRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KnowledgeDocument markProcessing(Long documentId) {
        KnowledgeDocument document = findDocument(documentId);
        document.setStatus(KnowledgeDocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        return repository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIndexed(Long documentId, int chunkCount) {
        KnowledgeDocument document = findDocument(documentId);
        document.setChunkCount(chunkCount);
        document.setStatus(KnowledgeDocumentStatus.INDEXED);
        document.setIndexedAt(LocalDateTime.now());
        document.setErrorMessage(null);
        repository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId, String errorMessage) {
        KnowledgeDocument document = findDocument(documentId);
        document.setStatus(KnowledgeDocumentStatus.FAILED);
        document.setErrorMessage(errorMessage);
        repository.save(document);
    }

    private KnowledgeDocument findDocument(Long documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
    }
}
