package com.g93.be.controller;

import com.g93.be.dto.ChatSessionResponse;
import com.g93.be.dto.CreateChatSessionRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.service.ChatOrchestratorService;
import com.g93.be.service.ChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ChatControllerRbacTest.SecurityTestConfig.class)
class ChatControllerRbacTest {

    @Autowired
    private ChatController controller;
    @Autowired
    private ChatOrchestratorService orchestratorService;
    @Autowired
    private ChatSessionService chatSessionService;

    private final Principal principal = () -> "doctor";

    @BeforeEach
    void resetMocks() {
        reset(orchestratorService, chatSessionService);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "USE_AI_CHAT"})
    void doctorWithChatPermissionCanCreateAndListSessions() {
        CreateChatSessionRequest request = new CreateChatSessionRequest("Knee discussion", null);
        ChatSessionResponse session = new ChatSessionResponse(
                12L, null, "Knee discussion", true, LocalDateTime.now(), LocalDateTime.now());
        PageResponse<ChatSessionResponse> page = new PageResponse<>(List.of(session), 0, 20, 1, 1, true);
        PageRequest pageable = PageRequest.of(0, 20);
        when(chatSessionService.create(request, "doctor")).thenReturn(session);
        when(chatSessionService.getSessions("doctor", pageable)).thenReturn(page);

        assertEquals(201, controller.createSession(request, principal).getStatusCode().value());
        assertEquals(page, controller.getSessions(pageable, principal).getBody());
        verify(chatSessionService).create(request, "doctor");
        verify(chatSessionService).getSessions("doctor", pageable);
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorWithoutChatPermissionCannotListSessions() {
        assertThrows(AccessDeniedException.class,
                () -> controller.getSessions(PageRequest.of(0, 20), principal));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "USE_AI_CHAT"})
    void adminWithChatPermissionCanUseOwnSessions() {
        PageRequest pageable = PageRequest.of(0, 20);
        PageResponse<ChatSessionResponse> page = new PageResponse<>(List.of(), 0, 20, 0, 0, true);
        when(chatSessionService.getSessions("doctor", pageable)).thenReturn(page);

        assertEquals(page, controller.getSessions(pageable, principal).getBody());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfig {

        @Bean
        ChatOrchestratorService chatOrchestratorService() {
            return mock(ChatOrchestratorService.class);
        }

        @Bean
        ChatSessionService chatSessionService() {
            return mock(ChatSessionService.class);
        }

        @Bean
        ChatController chatController(
                ChatOrchestratorService orchestratorService,
                ChatSessionService chatSessionService) {
            return new ChatController(orchestratorService, chatSessionService);
        }
    }
}
