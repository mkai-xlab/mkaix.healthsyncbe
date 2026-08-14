package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @TempDir
    Path knowledgeDir;

    @Mock
    private KnowledgeDocumentRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MedicalDocumentValidator medicalDocumentValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private KnowledgeDocumentDeletionService deletionService;

    @Test
    void deleteDelegatesInsideDocumentOperationCoordinator() {
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                repository, userRepository, RestClient.create(), properties(),
                medicalDocumentValidator, new KnowledgeDocumentOperationCoordinator(), deletionService,
                eventPublisher);

        service.delete(7L);

        verify(deletionService).delete(7L);
    }

    @Test
    void rejectedMedicalValidationDoesNotStoreDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "software.txt", "text/plain", "software deployment".getBytes());
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Document rejected: Software documentation"))
                .when(medicalDocumentValidator)
                .validate(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                repository, userRepository, RestClient.create(), properties(),
                medicalDocumentValidator, new KnowledgeDocumentOperationCoordinator(), deletionService,
                eventPublisher);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, null, "doctor"));

        verify(repository, never()).save(any(KnowledgeDocument.class));
        verifyNoInteractions(userRepository, eventPublisher);
        try (java.util.stream.Stream<Path> files = Files.list(knowledgeDir)) {
            org.junit.jupiter.api.Assertions.assertEquals(0, files.count());
        }
    }

    private ChatProperties properties() {
        return new ChatProperties(true, knowledgeDir.toString(), 1000, 1000,
                12, 0.6, 6000, 0.7, 1000);
    }
}
