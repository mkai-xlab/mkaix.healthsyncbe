package com.g93.be.service;

import com.g93.be.dto.KnowledgeBatchUploadResponse;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.entity.KnowledgeAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBatchIngestionServiceTest {

    @Mock
    private KnowledgeIngestionService ingestionService;

    @Test
    void uploadKeepsValidDocumentsWhenAnotherDocumentIsRejected() {
        KnowledgeBatchIngestionService service = new KnowledgeBatchIngestionService(ingestionService);
        MultipartFile accepted = file("accepted.pdf");
        MultipartFile duplicate = file("duplicate.pdf");
        KnowledgeDocumentResponse document = document(7L, "accepted.pdf");
        when(ingestionService.upload(accepted, null, KnowledgeAccessScope.ALL, "doctor"))
                .thenReturn(document);
        when(ingestionService.upload(duplicate, null, KnowledgeAccessScope.ALL, "doctor"))
                .thenThrow(new IllegalArgumentException("This document has already been uploaded"));

        KnowledgeBatchUploadResponse response = service.upload(
                List.of(accepted, duplicate), KnowledgeAccessScope.ALL, "doctor");

        assertEquals(2, response.totalFiles());
        assertEquals(1, response.acceptedCount());
        assertEquals(1, response.rejectedCount());
        assertEquals(document, response.items().getFirst().document());
        assertEquals("This document has already been uploaded", response.items().get(1).error());
    }

    @Test
    void uploadRejectsMoreThanTenDocuments() {
        KnowledgeBatchIngestionService service = new KnowledgeBatchIngestionService(ingestionService);
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> file("document-" + index + ".txt"))
                .map(MultipartFile.class::cast)
                .toList();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(files, KnowledgeAccessScope.ALL, "doctor"));

        assertEquals("A batch can contain at most 10 documents", exception.getMessage());
    }

    @Test
    void uploadRejectsEmptyBatchBeforeCallingSingleFileService() {
        KnowledgeBatchIngestionService service = new KnowledgeBatchIngestionService(ingestionService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(), KnowledgeAccessScope.ALL, "doctor"));

        assertEquals("At least one document file is required", exception.getMessage());
        verify(ingestionService, never()).upload(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadSanitizesUnexpectedPerFileFailureAndContinuesBatch() {
        KnowledgeBatchIngestionService service = new KnowledgeBatchIngestionService(ingestionService);
        MultipartFile failed = file("failed.pdf");
        MultipartFile accepted = file("accepted.pdf");
        KnowledgeDocumentResponse document = document(8L, "accepted.pdf");
        when(ingestionService.upload(failed, null, KnowledgeAccessScope.ALL, "doctor"))
                .thenThrow(new IllegalStateException("internal storage path"));
        when(ingestionService.upload(accepted, null, KnowledgeAccessScope.ALL, "doctor"))
                .thenReturn(document);

        KnowledgeBatchUploadResponse response = service.upload(
                List.of(failed, accepted), KnowledgeAccessScope.ALL, "doctor");

        assertEquals(1, response.acceptedCount());
        assertEquals(1, response.rejectedCount());
        assertEquals("Could not accept document", response.items().getFirst().error());
        assertEquals(document, response.items().get(1).document());
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "content".getBytes());
    }

    private KnowledgeDocumentResponse document(Long id, String name) {
        return new KnowledgeDocumentResponse(
                id, name, "FILE", null, name, "ALL", "PENDING", null, null,
                LocalDateTime.now(), null);
    }
}
