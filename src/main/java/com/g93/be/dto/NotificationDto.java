package com.g93.be.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String title,
        String message,
        String type,
        Boolean isRead,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime createdAt,
        Object data
) {
}
