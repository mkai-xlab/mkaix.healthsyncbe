package com.g93.be.dto;

import java.util.List;

public record KnowledgeBatchUploadResponse(
        int totalFiles,
        int acceptedCount,
        int rejectedCount,
        List<KnowledgeBatchUploadItemResponse> items) {
}
