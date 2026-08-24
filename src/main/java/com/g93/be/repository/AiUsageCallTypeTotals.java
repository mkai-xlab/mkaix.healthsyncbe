package com.g93.be.repository;

import com.g93.be.entity.AiCallType;

import java.math.BigDecimal;

public interface AiUsageCallTypeTotals {
    AiCallType getCallType();

    long getCalls();

    long getPromptTokens();

    long getCompletionTokens();

    long getTotalTokens();

    BigDecimal getCostUsd();
}
