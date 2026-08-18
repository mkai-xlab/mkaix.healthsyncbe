package com.g93.be.dto;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        Long examinationId,
        String title,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
