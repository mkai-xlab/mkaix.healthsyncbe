package com.g93.be.dto;

import java.time.LocalDateTime;

/**
 * Response describing the final KL decision while retaining the AI prediction.
 */
public record DiagnosisReviewResponse(
        Long reviewId,
        Long aiResultId,
        Long examinationId,
        Integer predictedKlGrade,
        Integer confirmedKlGrade,
        String decision,
        String reviewNote,
        Long reviewedByDoctorId,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime reviewedAt
) {
}
