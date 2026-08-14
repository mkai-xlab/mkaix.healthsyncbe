package com.g93.be.service;

import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.MedicalDocumentAssessment;
import com.g93.be.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class MedicalDocumentValidator {

    private static final int SAMPLE_COUNT = 3;

    private final KnowledgeDocumentReader documentReader;
    private final AiChatGateway aiChatGateway;
    private final ChatProperties properties;

    public void validate(byte[] bytes, String originalName, String contentType) {
        List<Document> documents = documentReader.read(bytes, originalName, contentType);
        String content = documents.stream()
                .filter(document -> document != null && document.isText())
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new IllegalArgumentException("No readable text was found in the document"));

        String samples = sample(content, properties.medicalValidationSampleChars());
        MedicalDocumentAssessment assessment = aiChatGateway.assessMedicalDocument(samples);
        if (assessment == null || !Boolean.TRUE.equals(assessment.medical())
                || assessment.confidence() == null
                || assessment.confidence() < properties.medicalValidationMinConfidence()) {
            String reason = assessment == null || assessment.reason() == null || assessment.reason().isBlank()
                    ? "the content is not clearly medical"
                    : assessment.reason().trim();
            throw new IllegalArgumentException("Document rejected: " + reason);
        }
    }

    String sample(String content, int sampleChars) {
        String normalized = content.replace('\u0000', ' ').trim();
        int size = Math.max(1, sampleChars);
        if (normalized.length() <= size * SAMPLE_COUNT) {
            return "[COMPLETE DOCUMENT]\n" + normalized;
        }

        int middleStart = Math.max(0, normalized.length() / 2 - size / 2);
        int endStart = normalized.length() - size;
        return "[BEGINNING SAMPLE]\n" + normalized.substring(0, size)
                + "\n\n[MIDDLE SAMPLE]\n" + normalized.substring(middleStart, middleStart + size)
                + "\n\n[ENDING SAMPLE]\n" + normalized.substring(endStart);
    }
}
