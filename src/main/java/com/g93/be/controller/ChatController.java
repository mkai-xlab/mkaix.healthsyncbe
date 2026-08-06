package com.g93.be.controller;

import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatQuestionRequest;
import com.g93.be.service.ChatOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('USE_AI_CHAT')")
    public ResponseEntity<ChatAnswerResponse> ask(
            @Valid @RequestBody ChatQuestionRequest request,
            Principal principal) {
        return ResponseEntity.ok(chatOrchestratorService.ask(request.question(), principal.getName()));
    }
}
