package com.g93.be.dto;

public record ChatSourceResponse(
        String sourceId,
        String title,
        String sourceType,
        String locator,
        Double score) {
}
