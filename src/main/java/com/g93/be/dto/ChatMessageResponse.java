package com.g93.be.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long sessionId,
        String role,
        String content,
        String route,
        Integer tokensUsed,
        LocalDateTime createdAt) {
}
