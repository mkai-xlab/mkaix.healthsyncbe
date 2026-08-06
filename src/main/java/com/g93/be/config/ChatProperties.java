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
        long reportSyncDelayMs) {
}
