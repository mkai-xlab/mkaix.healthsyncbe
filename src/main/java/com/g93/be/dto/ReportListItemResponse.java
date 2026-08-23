package com.g93.be.dto;

import java.time.LocalDateTime;

/**
 * Report metadata used by the generated-report list screen.
 */
public record ReportListItemResponse(
        Long reportId,
        Long examinationId,
        String encounterCode,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime visitTime,
        String patientCode,
        String patientName,
        Long doctorId,
        String doctorName,
        String fileName,
        Long fileSize,
        String contentType,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime generatedAt,
        String previewUrl,
        String downloadUrl) {
}
