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
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime createdAt,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime indexedAt) {
}
