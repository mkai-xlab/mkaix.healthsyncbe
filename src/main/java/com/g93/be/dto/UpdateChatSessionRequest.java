package com.g93.be.dto;

import jakarta.validation.constraints.Size;

public record UpdateChatSessionRequest(
        @Size(max = 160, message = "Title must not exceed 160 characters")
        String title,
        Boolean active) {
}
