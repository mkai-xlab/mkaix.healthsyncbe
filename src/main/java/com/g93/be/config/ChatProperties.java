package com.g93.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        boolean enabled,
        String knowledgeDir,
        long maxDocumentBytes,
        long maxUrlBytes,
        int retrievalTopK,
        double similarityThreshold,
        int medicalValidationSampleChars,
        double medicalValidationMinConfidence,
        long reportSyncDelayMs,
        Pricing pricing) {

    /**
     * USD list price of the configured chat model, per 1,000,000 tokens. There is no
     * provider-agnostic way to read this from the AI SDK, so it must be kept in sync by hand
     * whenever the provider changes its pricing.
     */
    public record Pricing(double inputPricePerMillionTokens, double outputPricePerMillionTokens) {
    }
}
