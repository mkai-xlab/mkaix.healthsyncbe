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
        LocalDateTime reviewedAt
) {
}
