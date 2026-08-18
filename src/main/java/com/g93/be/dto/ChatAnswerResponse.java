package com.g93.be.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatAnswerResponse(
        Long sessionId,
        Long messageId,
        String route,
        String answer,
        List<ChatSourceResponse> sources,
        String warning,
        LocalDateTime generatedAt,
        Integer tokensUsed) {
}
