package com.g93.be.service;

import com.g93.be.chat.MedicalRetrievalResult;
import com.g93.be.config.ChatProperties;
import com.g93.be.dto.ChatSourceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MedicalRagServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    void departmentHeadCannotSearchEveryOwnersPrivateReport() {
        ChatProperties properties = properties(12);
        MedicalRagService service = new MedicalRagService(vectorStore, properties);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        service.retrieve("report", "DEPARTMENT_HEAD", 42L);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        String filter = request.getValue().getFilterExpression().toString();
        assertTrue(filter.contains("Key[key=ownerUserId]"), filter);
        assertTrue(filter.contains("Key[key=assignedDoctorUserId]"), filter);
        assertTrue(filter.contains("Value[value=42]"), filter);
    }

    @Test
    void unsupportedRoleCannotSearchMedicalKnowledge() {
        ChatProperties properties = properties(12);
        MedicalRagService service = new MedicalRagService(vectorStore, properties);

        assertThrows(AccessDeniedException.class,
                () -> service.retrieve("report", "PATIENT", 42L));
    }

    @Test
    void retrievalUsesConfiguredTopTwelveChunks() {
        MedicalRagService service = new MedicalRagService(vectorStore, properties(12));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        service.retrieve("knee osteoarthritis", "DOCTOR", 42L);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertEquals(12, request.getValue().getTopK());
    }

    @Test
    void oversizedChunkIsDroppedWholeInsteadOfTruncatedMidSentence() {
        String firstChunk = "A".repeat(55_000);
        String secondChunk = "UNIQUE_TAIL_MARKER_" + "B".repeat(10_000);
        Document first = Document.builder().text(firstChunk)
                .metadata(Map.of("title", "Guideline part 1")).score(0.9).build();
        Document second = Document.builder().text(secondChunk)
                .metadata(Map.of("title", "Guideline part 2")).score(0.8).build();
        MedicalRagService service = new MedicalRagService(vectorStore, properties(12));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(first, second));

        MedicalRetrievalResult result = service.retrieve("kl grade", "DOCTOR", 42L);

        assertTrue(result.context().contains(firstChunk));
        assertFalse(result.context().contains("UNIQUE_TAIL_MARKER"));
    }

    @Test
    void pdfPageMetadataProducesPerPageCitation() {
        Document page12 = Document.builder().text("KL grade table")
                .metadata(Map.of("title", "OA Guideline", "reference", "guideline.pdf",
                        "sourceType", "FILE", "page", 12))
                .score(0.88).build();
        MedicalRagService service = new MedicalRagService(vectorStore, properties(12));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(page12));

        MedicalRetrievalResult result = service.retrieve("kl grade", "DOCTOR", 42L);

        ChatSourceResponse source = result.sources().getFirst();
        assertEquals("guideline.pdf, tr. 12", source.locator());
    }

    private ChatProperties properties(int topK) {
        return new ChatProperties(true, "knowledge", 1000, 1000, topK, 0.6, 6000, 0.7, 1000,
                new ChatProperties.Pricing(1.5, 9.0));
    }
}
