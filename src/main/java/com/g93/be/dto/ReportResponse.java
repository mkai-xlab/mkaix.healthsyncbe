package com.g93.be.dto;

import java.time.LocalDateTime;

public record ReportResponse(
        Long reportId,
        Long examinationId,
        String fileName,
        Long fileSize,
        String contentType,
        LocalDateTime generatedAt,
        String previewUrl,
        String downloadUrl) {
}
