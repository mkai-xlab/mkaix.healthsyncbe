package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQuestionRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question) {
}
