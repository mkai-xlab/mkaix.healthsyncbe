package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.dto.AiUsageSummaryResponse;
import com.g93.be.entity.AiCallType;
import com.g93.be.entity.AiUsageLog;
import com.g93.be.repository.AiUsageCallTypeTotals;
import com.g93.be.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Records the token usage of every LLM call (router classification, chat answers, and
 * document-upload validation) so cost can be audited later. This is best-effort telemetry:
 * a failure here must never fail the AI call it is tracking, so every exception is caught
 * and logged instead of propagated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageTrackingService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final AiUsageLogRepository repository;
    private final ChatProperties chatProperties;

    @Transactional
    public void record(AiCallType callType, String model, Integer promptTokens, Integer completionTokens) {
        try {
            AiUsageLog entry = new AiUsageLog();
            entry.setCallType(callType);
            entry.setModel(model);
            entry.setPromptTokens(promptTokens);
            entry.setCompletionTokens(completionTokens);
            entry.setTotalTokens(sum(promptTokens, completionTokens));
            entry.setCostUsd(cost(promptTokens, completionTokens));
            repository.save(entry);
        } catch (Exception exception) {
            log.warn("Failed to record AI usage for {} ({}): {}", callType, model, exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AiUsageSummaryResponse summarize(LocalDate from, LocalDate to) {
        List<AiUsageCallTypeTotals> totals = repository.summarizeByCallType(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        List<AiUsageSummaryResponse.Breakdown> breakdown = totals.stream()
                .map(row -> new AiUsageSummaryResponse.Breakdown(
                        row.getCallType(),
                        row.getCalls(),
                        row.getPromptTokens(),
                        row.getCompletionTokens(),
                        row.getTotalTokens(),
                        row.getCostUsd()))
                .toList();

        long totalCalls = breakdown.stream().mapToLong(AiUsageSummaryResponse.Breakdown::calls).sum();
        long totalPromptTokens = breakdown.stream().mapToLong(AiUsageSummaryResponse.Breakdown::promptTokens).sum();
        long totalCompletionTokens = breakdown.stream()
                .mapToLong(AiUsageSummaryResponse.Breakdown::completionTokens).sum();
        long totalTokens = breakdown.stream().mapToLong(AiUsageSummaryResponse.Breakdown::totalTokens).sum();
        BigDecimal totalCostUsd = breakdown.stream()
                .map(AiUsageSummaryResponse.Breakdown::costUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AiUsageSummaryResponse(
                from, to, totalCalls, totalPromptTokens, totalCompletionTokens, totalTokens,
                totalCostUsd, breakdown);
    }

    private Integer sum(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        return (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
    }

    private BigDecimal cost(Integer promptTokens, Integer completionTokens) {
        ChatProperties.Pricing pricing = chatProperties.pricing();
        if (pricing == null || (promptTokens == null && completionTokens == null)) {
            return null;
        }
        BigDecimal inputCost = tokensToUsd(promptTokens, pricing.inputPricePerMillionTokens());
        BigDecimal outputCost = tokensToUsd(completionTokens, pricing.outputPricePerMillionTokens());
        return inputCost.add(outputCost);
    }

    private BigDecimal tokensToUsd(Integer tokens, double pricePerMillion) {
        if (tokens == null || tokens == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .multiply(BigDecimal.valueOf(pricePerMillion), MathContext.DECIMAL64)
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
    }
}
