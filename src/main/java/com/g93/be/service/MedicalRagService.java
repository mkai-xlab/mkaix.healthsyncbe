package com.g93.be.service;

import com.g93.be.chat.MedicalRetrievalResult;
import com.g93.be.config.ChatProperties;
import com.g93.be.dto.ChatSourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class MedicalRagService {

    private static final int MAX_CONTEXT_CHARS = 16_000;

    private final VectorStore vectorStore;
    private final ChatProperties properties;

    public MedicalRetrievalResult retrieve(String question, String roleCode, Long userId) {
        String scopeFilter = scopeFilter(roleCode, userId);
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.retrievalTopK())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression(scopeFilter)
                .build();
        List<Document> matches = vectorStore.similaritySearch(request);
        if (matches == null || matches.isEmpty()) {
            return new MedicalRetrievalResult("", List.of());
        }

        StringBuilder context = new StringBuilder();
        Map<String, ChatSourceResponse> uniqueSources = new LinkedHashMap<>();
        for (Document document : matches) {
            if (context.length() >= MAX_CONTEXT_CHARS) {
                break;
            }
            Map<String, Object> metadata = document.getMetadata();
            String title = stringValue(metadata.get("title"), "Medical knowledge source");
            String reference = stringValue(metadata.get("reference"), stringValue(metadata.get("sourceKey"), null));
            Double score = document.getScore();
            String text = document.getText();
            if (text != null && !text.isBlank()) {
                int remaining = MAX_CONTEXT_CHARS - context.length();
                context.append("[SOURCE: ").append(title).append("]\n")
                        .append(text, 0, Math.min(text.length(), remaining)).append("\n\n");
            }
            String key = reference == null ? title : reference;
            uniqueSources.putIfAbsent(key,
                    new ChatSourceResponse(key, title,
                            stringValue(metadata.get("sourceType"), "MEDICAL_DOCUMENT"), reference, score));
        }
        return new MedicalRetrievalResult(context.toString(), new ArrayList<>(uniqueSources.values()));
    }

    private String scopeFilter(String roleCode, Long userId) {
        String published = "publicationStatus == 'PUBLISHED' && ";
        if ("ADMIN".equals(roleCode)) {
            return published + "(accessScope == 'ALL' || accessScope == 'ADMIN')";
        }
        if ("HEAD_OF_DEPARTMENT".equals(roleCode) || "DEPARTMENT_HEAD".equals(roleCode)) {
            // OWNER documents contain report-level clinical data. Department roles
            // do not carry a department identifier in the vector metadata yet, so
            // do not broaden this scope to every owner's private report.
            return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                    + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                    + " || assignedDoctorUserId == " + userId + ")))";
        }
        if ("DOCTOR".equals(roleCode)) {
            return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                    + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                    + " || assignedDoctorUserId == " + userId + ")))";
        }
        throw new AccessDeniedException("Role is not allowed to search medical knowledge");
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
