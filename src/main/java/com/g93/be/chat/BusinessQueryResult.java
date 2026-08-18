package com.g93.be.chat;

import com.g93.be.dto.ChatSourceResponse;

import java.util.List;

public record BusinessQueryResult(String context, List<ChatSourceResponse> sources) {
}
