package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.entity.KnowledgeSourceType;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void getAllReturnsFilteredDocumentPage() {
        KnowledgeIngestionService service = service();
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        KnowledgeDocument document = document();
        when(repository.search("knee", KnowledgeSourceType.FILE, KnowledgeDocumentStatus.INDEXED,
                KnowledgeAccessScope.ALL, pageable))
                .thenReturn(new PageImpl<>(List.of(document), pageable, 1));

        PageResponse<KnowledgeDocumentResponse> response = service.getAll(
                "  knee  ", KnowledgeSourceType.FILE, KnowledgeDocumentStatus.INDEXED,
                KnowledgeAccessScope.ALL, pageable);

        assertEquals(1, response.totalElements());
        assertEquals(20, response.pageSize());
        assertEquals("Knee guideline", response.content().getFirst().title());
        assertEquals("knee-guideline.pdf", response.content().getFirst().originalName());
        assertEquals("/api/v1/knowledge-documents/8/content", response.content().getFirst().contentUrl());
        assertEquals("/api/v1/knowledge-documents/8/preview", response.content().getFirst().previewUrl());
        assertEquals("/api/v1/knowledge-documents/8/download", response.content().getFirst().downloadUrl());
        verify(repository).search("knee", KnowledgeSourceType.FILE, KnowledgeDocumentStatus.INDEXED,
                KnowledgeAccessScope.ALL, pageable);
    }

    @Test
    void getAllTreatsBlankKeywordAsNoSearchTerm() {
        KnowledgeIngestionService service = service();
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.search(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<KnowledgeDocumentResponse> response = service.getAll("   ", null, null, null, pageable);

        assertEquals(0, response.totalElements());
        verify(repository).search(null, null, null, null, pageable);
    }

    @Test
    void getFileReturnsStoredDocumentWithMetadata() throws Exception {
        Path stored = knowledgeDir.resolve("knee-guideline.pdf");
        Files.writeString(stored, "pdf bytes");
        KnowledgeDocument document = document();
        document.setStoragePath(stored.toString());
        document.setContentType("application/pdf");
        when(repository.findById(8L)).thenReturn(java.util.Optional.of(document));

        KnowledgeIngestionService.KnowledgeDocumentFile file = service().getFile(8L);

        assertEquals("knee-guideline.pdf", file.fileName());
        assertEquals("application/pdf", file.contentType());
        assertEquals(Files.size(stored), file.fileSize());
        assertEquals("pdf bytes", Files.readString(file.resource().getFile().toPath()));
    }

    @Test
    void getFileRejectsPathOutsideKnowledgeDirectory() throws Exception {
        Path outside = Files.createTempFile("knowledge-outside", ".txt");
        KnowledgeDocument document = document();
        document.setStoragePath(outside.toString());
        when(repository.findById(8L)).thenReturn(java.util.Optional.of(document));

        assertThrows(com.g93.be.exception.ResourceNotFoundException.class, () -> service().getFile(8L));
    }

    @Test
    void getTextExtractsReadableContentFromStoredDocument() throws Exception {
        Path stored = knowledgeDir.resolve("knee-guideline.txt");
        Files.writeString(stored, "KL4 means severe osteoarthritis");
        KnowledgeDocument document = document();
        document.setOriginalName("knee-guideline.txt");
        document.setStoragePath(stored.toString());
        document.setContentType("text/plain");
        when(repository.findById(8L)).thenReturn(java.util.Optional.of(document));

        String content = service().getText(8L);

        assertEquals("KL4 means severe osteoarthritis", content);
    }

    @Test
    void deleteDelegatesInsideDocumentOperationCoordinator() {
        KnowledgeIngestionService service = service();

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
        KnowledgeIngestionService service = service();

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

    private KnowledgeIngestionService service() {
        return new KnowledgeIngestionService(
                repository, userRepository, RestClient.create(), properties(),
                medicalDocumentValidator, new KnowledgeDocumentReader(),
                new KnowledgeDocumentOperationCoordinator(), deletionService,
                eventPublisher);
    }

    private KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(8L);
        document.setTitle("Knee guideline");
        document.setSourceType(KnowledgeSourceType.FILE);
        document.setOriginalName("knee-guideline.pdf");
        document.setStoragePath(knowledgeDir.resolve("knee-guideline.pdf").toString());
        document.setAccessScope(KnowledgeAccessScope.ALL);
        document.setStatus(KnowledgeDocumentStatus.INDEXED);
        document.setCreatedAt(LocalDateTime.of(2026, 8, 8, 9, 0));
        return document;
    }
}
