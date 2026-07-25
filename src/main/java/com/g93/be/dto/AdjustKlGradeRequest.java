package com.g93.be.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request submitted by a doctor or department head to override an AI-predicted KL grade.
 */
public record AdjustKlGradeRequest(
        @NotNull(message = "Confirmed KL grade is required")
        @Min(value = 0, message = "Confirmed KL grade must be between 0 and 4")
        @Max(value = 4, message = "Confirmed KL grade must be between 0 and 4")
        Integer confirmedKlGrade,

        @NotBlank(message = "Review note is required")
        @Size(max = 2000, message = "Review note must not exceed 2000 characters")
        String reviewNote
) {
}
