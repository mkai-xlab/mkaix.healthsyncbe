package com.g93.be.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String title,
        String message,
        String type,
        Boolean isRead,
        LocalDateTime createdAt
) {
}
