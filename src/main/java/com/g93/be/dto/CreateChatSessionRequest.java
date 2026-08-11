package com.g93.be.dto;

import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
        @Size(max = 160, message = "Title must not exceed 160 characters")
        String title,
        Long examinationId) {
}
