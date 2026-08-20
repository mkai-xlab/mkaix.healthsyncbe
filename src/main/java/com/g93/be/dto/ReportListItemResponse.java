package com.g93.be.dto;

import java.time.LocalDateTime;

/**
 * Report metadata used by the generated-report list screen.
 */
public record ReportListItemResponse(
        Long reportId,
        Long examinationId,
        String encounterCode,
        LocalDateTime visitTime,
        String patientCode,
        String patientName,
        Long doctorId,
        String doctorName,
        String fileName,
        Long fileSize,
        String contentType,
        LocalDateTime generatedAt,
        String previewUrl,
        String downloadUrl) {
}
