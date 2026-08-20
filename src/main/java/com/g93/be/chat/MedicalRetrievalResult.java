package com.g93.be.chat;

import com.g93.be.dto.ChatSourceResponse;

import java.util.List;

public record MedicalRetrievalResult(String context, List<ChatSourceResponse> sources) {
    public boolean isEmpty() {
        return sources == null || sources.isEmpty();
    }
}
