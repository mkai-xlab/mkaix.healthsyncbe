package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.controller.ChatController;
import com.g93.be.controller.KnowledgeController;
import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatMessageResponse;
import com.g93.be.dto.ChatSessionResponse;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.exception.GlobalExceptionHandler;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.service.ChatOrchestratorService;
import com.g93.be.service.ChatSessionService;
import com.g93.be.service.KnowledgeBatchIngestionService;
import com.g93.be.service.KnowledgeIngestionService;
import com.g93.be.service.ReportKnowledgeSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(MedicalAiChatbotIntegrationTest.TestConfig.class)
class MedicalAiChatbotIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 30);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ChatOrchestratorService chatOrchestratorService;
    @Autowired
    private ChatSessionService chatSessionService;
    @Autowired
    private KnowledgeIngestionService knowledgeIngestionService;
    @Autowired
    private KnowledgeBatchIngestionService batchIngestionService;
    @Autowired
    private ReportKnowledgeSyncService reportKnowledgeSyncService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reset(chatOrchestratorService, chatSessionService, knowledgeIngestionService,
                batchIngestionService, reportKnowledgeSyncService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void chatWithAiReturnsContextualAnswerForAuthorizedDoctor() throws Exception {
        ChatAnswerResponse response = new ChatAnswerResponse(
                12L, 84L, "MEDICAL_RAG", "KL grade 3 indicates definite joint-space narrowing.",
                List.of(), "Clinical review is required.", NOW, 42);
        when(chatOrchestratorService.ask(12L, "Explain KL grade 3", "doctor"))
                .thenReturn(response);

        mockMvc.perform(post("/chat/ask")
                        .with(chatUser("doctor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":12,"question":"Explain KL grade 3"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(12))
                .andExpect(jsonPath("$.messageId").value(84))
                .andExpect(jsonPath("$.route").value("MEDICAL_RAG"))
                .andExpect(jsonPath("$.answer")
                        .value("KL grade 3 indicates definite joint-space narrowing."))
                .andExpect(jsonPath("$.tokensUsed").value(42));

        verify(chatOrchestratorService).ask(12L, "Explain KL grade 3", "doctor");
    }

    @Test
    void chatWithAiRejectsBlankQuestionBeforeCallingService() throws Exception {
        mockMvc.perform(post("/chat/ask")
                        .with(chatUser("doctor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":12,"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("question: Question is required"));

        verify(chatOrchestratorService, never()).ask(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void viewAiChatHistoryReturnsOwnPaginatedSessionsAndMessages() throws Exception {
        ChatSessionResponse session = new ChatSessionResponse(
                12L, 31L, "Knee follow-up", true, NOW.minusHours(1), NOW);
        ChatMessageResponse question = new ChatMessageResponse(
                83L, 12L, "USER", "Explain KL grade 3", null, null, NOW.minusMinutes(1));
        ChatMessageResponse answer = new ChatMessageResponse(
                84L, 12L, "ASSISTANT", "Accessible evidence summary", "MEDICAL_RAG", 42, NOW);
        when(chatSessionService.getSessions(eq("doctor"), argThat(pageable ->
                pageable.getPageNumber() == 1 && pageable.getPageSize() == 5)))
                .thenReturn(new PageResponse<>(List.of(session), 1, 5, 6, 2, true));
        when(chatSessionService.getMessages(eq(12L), eq("doctor"), argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 10)))
                .thenReturn(new PageResponse<>(List.of(question, answer), 0, 10, 2, 1, true));

        mockMvc.perform(get("/chat/sessions")
                        .with(chatUser("doctor"))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].id").value(12))
                .andExpect(jsonPath("$.content[0].examinationId").value(31));

        mockMvc.perform(get("/chat/sessions/12/messages")
                        .with(chatUser("doctor"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content[1].route").value("MEDICAL_RAG"));

        verify(chatSessionService).getSessions(eq("doctor"), argThat(pageable ->
                pageable.getPageNumber() == 1 && pageable.getPageSize() == 5));
        verify(chatSessionService).getMessages(eq(12L), eq("doctor"), argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
    }

    @Test
    void uploadMedicalDocumentAcceptsMultipartAndAuthenticatedOwner() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "oa-guideline.pdf", "application/pdf", "medical pdf".getBytes());
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse(
                41L, "OA guideline", "FILE", null, "oa-guideline.pdf", "DOCTOR",
                "PENDING", null, null, NOW, null);
        when(knowledgeIngestionService.upload(
                org.mockito.ArgumentMatchers.any(), eq("OA guideline"),
                eq(com.g93.be.entity.KnowledgeAccessScope.DOCTOR), eq("doctor")))
                .thenReturn(response);

        mockMvc.perform(multipart("/knowledge-documents/upload")
                        .file(file)
                        .param("title", "OA guideline")
                        .param("accessScope", "DOCTOR")
                        .with(knowledgeManager("doctor")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.originalName").value("oa-guideline.pdf"))
                .andExpect(jsonPath("$.accessScope").value("DOCTOR"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(knowledgeIngestionService).upload(
                org.mockito.ArgumentMatchers.argThat(upload ->
                        "oa-guideline.pdf".equals(upload.getOriginalFilename())),
                eq("OA guideline"), eq(com.g93.be.entity.KnowledgeAccessScope.DOCTOR), eq("doctor"));
    }

    @Test
    void deleteMedicalDocumentReturnsNoContentEvenWhileIndexingMayBeInProgress() throws Exception {
        mockMvc.perform(delete("/knowledge-documents/41")
                        .with(knowledgeManager("doctor")))
                .andExpect(status().isNoContent());

        verify(knowledgeIngestionService).delete(41L);
    }

    @Test
    void deleteMedicalDocumentReturnsNotFoundFromService() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Knowledge document not found"))
                .when(knowledgeIngestionService).delete(999L);

        mockMvc.perform(delete("/knowledge-documents/999")
                        .with(knowledgeManager("doctor")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Knowledge document not found"));
    }

    @Test
    void medicalKnowledgeOperationsRequireManagementPermission() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "oa-guideline.pdf", "application/pdf", "medical pdf".getBytes());

        mockMvc.perform(multipart("/knowledge-documents/upload")
                        .file(file)
                        .with(chatUser("doctor")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/knowledge-documents/41")
                        .with(chatUser("doctor")))
                .andExpect(status().isForbidden());

        verify(knowledgeIngestionService, never()).upload(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(knowledgeIngestionService, never()).delete(org.mockito.ArgumentMatchers.anyLong());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
            chatUser(String username) {
        return user(username).authorities(
                new SimpleGrantedAuthority("ROLE_DOCTOR"),
                new SimpleGrantedAuthority("USE_AI_CHAT"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
            knowledgeManager(String username) {
        return user(username).authorities(
                new SimpleGrantedAuthority("ROLE_DOCTOR"),
                new SimpleGrantedAuthority("MANAGE_MEDICAL_KNOWLEDGE"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableMethodSecurity
    @EnableWebSecurity
    static class TestConfig implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(
                List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new PageableHandlerMethodArgumentResolver());
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .build();
        }

        @Bean
        ChatOrchestratorService chatOrchestratorService() {
            return mock(ChatOrchestratorService.class);
        }

        @Bean
        ChatSessionService chatSessionService() {
            return mock(ChatSessionService.class);
        }

        @Bean
        KnowledgeIngestionService knowledgeIngestionService() {
            return mock(KnowledgeIngestionService.class);
        }

        @Bean
        KnowledgeBatchIngestionService knowledgeBatchIngestionService() {
            return mock(KnowledgeBatchIngestionService.class);
        }

        @Bean
        ReportKnowledgeSyncService reportKnowledgeSyncService() {
            return mock(ReportKnowledgeSyncService.class);
        }

        @Bean
        ChatController chatController(
                ChatOrchestratorService orchestratorService,
                ChatSessionService sessionService) {
            return new ChatController(orchestratorService, sessionService);
        }

        @Bean
        KnowledgeController knowledgeController(
                KnowledgeIngestionService ingestionService,
                KnowledgeBatchIngestionService batchIngestionService,
                ReportKnowledgeSyncService reportKnowledgeSyncService) {
            return new KnowledgeController(ingestionService, batchIngestionService, reportKnowledgeSyncService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
