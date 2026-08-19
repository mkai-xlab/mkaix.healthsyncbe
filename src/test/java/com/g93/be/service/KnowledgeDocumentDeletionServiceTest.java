package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentDeletionServiceTest {

    @TempDir
    Path knowledgeDir;

    @Mock
    private KnowledgeDocumentRepository repository;
    @Mock
    private VectorStore vectorStore;

    @Test
    void deleteRemovesVectorsMetadataAndStoredFile() throws Exception {
        Path storedFile = knowledgeDir.resolve("medical.txt");
        Files.writeString(storedFile, "medical content");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(7L);
        document.setSourceKey("file:checksum");
        document.setStoragePath(storedFile.toString());
        document.setStatus(KnowledgeDocumentStatus.PENDING);
        when(repository.findById(7L)).thenReturn(Optional.of(document));
        KnowledgeDocumentDeletionService service = new KnowledgeDocumentDeletionService(
                repository, vectorStore, properties());

        service.delete(7L);

        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(repository).delete(document);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void deleteUrlDocumentRemovesPendingVectorsAndMetadataWithoutLocalFile() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(8L);
        document.setSourceKey("url:checksum");
        document.setStatus(KnowledgeDocumentStatus.PROCESSING);
        document.setStoragePath(null);
        when(repository.findById(8L)).thenReturn(Optional.of(document));
        KnowledgeDocumentDeletionService service = new KnowledgeDocumentDeletionService(
                repository, vectorStore, properties());

        service.delete(8L);

        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(repository).delete(document);
    }

    @Test
    void deleteMissingDocumentDoesNotTouchVectorStoreOrRepositoryDelete() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        KnowledgeDocumentDeletionService service = new KnowledgeDocumentDeletionService(
                repository, vectorStore, properties());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(999L));

        verifyNoInteractions(vectorStore);
        verify(repository, never()).delete(any());
    }

    private ChatProperties properties() {
        return new ChatProperties(true, knowledgeDir.toString(), 1000, 1000,
                12, 0.6, 6000, 0.7, 1000);
    }
}
