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
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        LocalDateTime generatedAt,
        Integer tokensUsed) {
}
