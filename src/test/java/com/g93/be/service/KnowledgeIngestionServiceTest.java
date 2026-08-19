package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.chat.KnowledgeIndexRequestedEvent;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeSourceType;
import com.g93.be.entity.User;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestionService(
                repository, userRepository, RestClient.create(), properties(),
                medicalDocumentValidator, new KnowledgeDocumentOperationCoordinator(), deletionService,
                eventPublisher);
    }

    @Test
    void deleteDelegatesInsideDocumentOperationCoordinator() {
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
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, null, "doctor"));

        verify(repository, never()).save(any(KnowledgeDocument.class));
        verifyNoInteractions(userRepository, eventPublisher);
        try (java.util.stream.Stream<Path> files = Files.list(knowledgeDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void uploadStoresValidatedFilePersistsPendingMetadataAndRequestsAsyncIndexing() throws Exception {
        byte[] content = "Knee osteoarthritis KL grading medical guideline".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../knee-guideline.txt", "text/plain", content);
        User doctor = new User();
        doctor.setId(7L);
        doctor.setUsername("doctor");
        when(repository.existsBySourceKey(any(String.class))).thenReturn(false);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(repository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(41L);
            return document;
        });

        KnowledgeDocumentResponse response = service.upload(
                file, "  Knee OA guideline  ", KnowledgeAccessScope.DOCTOR, "doctor");

        assertEquals(41L, response.id());
        assertEquals("Knee OA guideline", response.title());
        assertEquals("knee-guideline.txt", response.originalName());
        assertEquals("FILE", response.sourceType());
        assertEquals("DOCTOR", response.accessScope());
        assertEquals("PENDING", response.status());
        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).save(documentCaptor.capture());
        KnowledgeDocument stored = documentCaptor.getValue();
        assertEquals(KnowledgeSourceType.FILE, stored.getSourceType());
        assertEquals(doctor, stored.getUploadedBy());
        assertTrue(stored.getSourceKey().startsWith("file:"));
        assertTrue(Files.exists(Path.of(stored.getStoragePath())));
        assertEquals("Knee osteoarthritis KL grading medical guideline",
                Files.readString(Path.of(stored.getStoragePath())));
        verify(medicalDocumentValidator).validate(content, "../knee-guideline.txt", "text/plain");
        verify(eventPublisher).publishEvent(new KnowledgeIndexRequestedEvent(41L));
    }

    @Test
    void uploadRejectsUnsupportedExtensionBeforeMedicalValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1, 2, 3});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, KnowledgeAccessScope.ALL, "doctor"));

        assertEquals("Only PDF, DOC, DOCX, and TXT documents are supported", error.getMessage());
        verifyNoInteractions(medicalDocumentValidator, userRepository, eventPublisher);
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsDuplicateWithoutStoringOrIndexingAgain() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "guideline.pdf", "application/pdf", "same medical content".getBytes());
        when(repository.existsBySourceKey(any(String.class))).thenReturn(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, KnowledgeAccessScope.ALL, "doctor"));

        assertEquals("This document has already been uploaded", error.getMessage());
        verifyNoInteractions(medicalDocumentValidator, userRepository, eventPublisher);
        verify(repository, never()).save(any());
        assertFalse(Files.exists(knowledgeDir.resolve("guideline.pdf")));
    }

    private ChatProperties properties() {
        return new ChatProperties(true, knowledgeDir.toString(), 1000, 1000,
                12, 0.6, 6000, 0.7, 1000);
    }
}
