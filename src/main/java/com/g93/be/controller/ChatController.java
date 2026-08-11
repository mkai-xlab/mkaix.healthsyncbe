package com.g93.be.controller;

import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatQuestionRequest;
import com.g93.be.dto.ChatMessageResponse;
import com.g93.be.dto.ChatSessionResponse;
import com.g93.be.dto.CreateChatSessionRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.UpdateChatSessionRequest;
import com.g93.be.service.ChatOrchestratorService;
import com.g93.be.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatSessionService chatSessionService;

    private static final String CHAT_ACCESS =
            "hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('USE_AI_CHAT')";

    @PostMapping("/ask")
    @PreAuthorize(CHAT_ACCESS)
    public ResponseEntity<ChatAnswerResponse> ask(
            @Valid @RequestBody ChatQuestionRequest request,
            Principal principal) {
        return ResponseEntity.ok(chatOrchestratorService.ask(
                request.sessionId(), request.question(), principal.getName()));
    }

    @PostMapping("/sessions")
    @PreAuthorize(CHAT_ACCESS)
    public ResponseEntity<ChatSessionResponse> createSession(
            @Valid @RequestBody CreateChatSessionRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatSessionService.create(request, principal.getName()));
    }

    @GetMapping("/sessions")
    @PreAuthorize(CHAT_ACCESS)
    public ResponseEntity<PageResponse<ChatSessionResponse>> getSessions(
            @PageableDefault(size = 20) Pageable pageable,
            Principal principal) {
        return ResponseEntity.ok(chatSessionService.getSessions(principal.getName(), pageable));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize(CHAT_ACCESS)
    public ResponseEntity<PageResponse<ChatMessageResponse>> getMessages(
            @PathVariable Long sessionId,
            @PageableDefault(size = 50) Pageable pageable,
            Principal principal) {
        return ResponseEntity.ok(chatSessionService.getMessages(sessionId, principal.getName(), pageable));
    }

    @PatchMapping("/sessions/{sessionId}")
    @PreAuthorize(CHAT_ACCESS)
    public ResponseEntity<ChatSessionResponse> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateChatSessionRequest request,
            Principal principal) {
        return ResponseEntity.ok(chatSessionService.update(sessionId, request, principal.getName()));
    }
}
