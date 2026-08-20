package com.g93.be.dto;

import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(
        Long id,
        String title,
        String sourceType,
        String sourceUrl,
        String originalName,
        String contentUrl,
        String previewUrl,
        String downloadUrl,
        String accessScope,
        String status,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime indexedAt) {
}
