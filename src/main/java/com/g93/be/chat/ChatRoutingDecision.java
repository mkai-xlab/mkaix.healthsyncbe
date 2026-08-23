package com.g93.be.chat;

public record ChatRoutingDecision(
        ChatRoute route,
        BusinessQueryIntent businessIntent,
        Long entityId,
        String dateFrom,
        String dateTo,
        Integer klGrade,
        String retrievalQuery,
        String clarificationQuestion) {
}
