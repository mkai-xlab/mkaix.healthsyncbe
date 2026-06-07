package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendNotificationRequest(
        @NotNull(message = "User ID cannot be null")
        Long userId,

        @NotBlank(message = "Title cannot be blank")
        String title,

        @NotBlank(message = "Message cannot be blank")
        String message,

        String type
) {
}
