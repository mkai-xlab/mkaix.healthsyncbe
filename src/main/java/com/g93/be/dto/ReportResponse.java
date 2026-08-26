package com.g93.be.dto;

import java.time.LocalDateTime;

public record ReportResponse(
        Long reportId,
        Long examinationId,
        String fileName,
        Long fileSize,
        String contentType,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime generatedAt,
        String previewUrl,
        String downloadUrl) {
}
