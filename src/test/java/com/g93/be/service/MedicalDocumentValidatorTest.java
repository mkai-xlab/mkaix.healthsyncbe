package com.g93.be.service;

import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.MedicalDocumentAssessment;
import com.g93.be.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalDocumentValidatorTest {

    @Mock
    private AiChatGateway aiChatGateway;

    @Test
    void acceptsClearlyMedicalDocument() {
        MedicalDocumentValidator validator = validator(0.7);
        when(aiChatGateway.assessMedicalDocument(contains("osteoarthritis")))
                .thenReturn(new MedicalDocumentAssessment(true, 0.96, "Clinical guideline"));

        validator.validate("Knee osteoarthritis diagnosis and treatment guideline"
                        .getBytes(StandardCharsets.UTF_8),
                "guideline.txt", "text/plain");
    }

    @Test
    void rejectsNonMedicalDocument() {
        MedicalDocumentValidator validator = validator(0.7);
        when(aiChatGateway.assessMedicalDocument(contains("software deployment")))
                .thenReturn(new MedicalDocumentAssessment(false, 0.99, "Software documentation"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate("General software deployment instructions"
                                .getBytes(StandardCharsets.UTF_8),
                        "manual.txt", "text/plain"));

        assertEquals("Document rejected: Software documentation", exception.getMessage());
    }

    @Test
    void rejectsAmbiguousAssessmentBelowConfidenceThreshold() {
        MedicalDocumentValidator validator = validator(0.7);
        when(aiChatGateway.assessMedicalDocument(contains("health")))
                .thenReturn(new MedicalDocumentAssessment(true, 0.55, "Insufficient medical detail"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("A short article about health"
                                .getBytes(StandardCharsets.UTF_8),
                        "article.txt", "text/plain"));
    }

    @Test
    void samplesBeginningMiddleAndEndOfLargeDocument() {
        MedicalDocumentValidator validator = validator(0.7);
        String content = "AAAA" + "x".repeat(12) + "MMMM" + "y".repeat(12) + "ZZZZ";

        String samples = validator.sample(content, 4);

        assertEquals("[BEGINNING SAMPLE]\nAAAA\n\n[MIDDLE SAMPLE]\nMMMM\n\n[ENDING SAMPLE]\nZZZZ", samples);
    }

    private MedicalDocumentValidator validator(double confidence) {
        ChatProperties properties = new ChatProperties(
                true, "knowledge", 1000, 1000, 12, 0.6, 6000, confidence, 1000);
        return new MedicalDocumentValidator(new KnowledgeDocumentReader(), aiChatGateway, properties);
    }
}
