package com.g93.be.dto;

public record KnowledgeBatchUploadItemResponse(
        String originalName,
        boolean accepted,
        KnowledgeDocumentResponse document,
        String error) {
}
