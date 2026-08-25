package com.g93.be.dto;

import com.g93.be.entity.AiCallType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AiUsageSummaryResponse(
        LocalDate from,
        LocalDate to,
        long totalCalls,
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalTokens,
        BigDecimal totalCostUsd,
        List<Breakdown> byCallType) {

    public record Breakdown(
            AiCallType callType,
            long calls,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            BigDecimal costUsd) {
    }
}
