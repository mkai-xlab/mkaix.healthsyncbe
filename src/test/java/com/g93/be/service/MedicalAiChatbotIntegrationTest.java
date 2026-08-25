package com.g93.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.ChatRoute;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatQuestionRequest;
import com.g93.be.dto.CreateChatSessionRequest;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.chat.enabled=true")
@Transactional
public class MedicalAiChatbotIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    private AiChatGateway aiChatGateway;

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Role doctorRole;
    private Doctor doctorUser;
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Cleanup database tables in reverse dependency order
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        userRepository.deleteAll();

        // Setup Roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        // Setup doctor user
        doctorUser = new Doctor();
        doctorUser.setUsername("doc_chat");
        doctorUser.setPassword(passwordEncoder.encode("password"));
        doctorUser.setFullName("Doctor Chat");
        doctorUser.setEmail("doc.chat@hospital.com");
        doctorUser.setPhone("0775555555");
        doctorUser.setRole(doctorRole);
        doctorUser.setStatus(UserStatus.ACTIVE);
        doctorUser.setIsFirstActivated(false);
        doctorUser.setYearsOfExperience(5);
        doctorUser = userRepository.save(doctorUser);

        // Generate tokens
        com.g93.be.dto.PermissionResponse useChatPerm = new com.g93.be.dto.PermissionResponse(1L, "USE_AI_CHAT", "Use Chat", 1, "USE_AI_CHAT", null);
        doctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser, List.of(useChatPerm)));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void testCreateChatSession_Success() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest("New Session", null);

        mockMvc.perform(post("/chat/sessions")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("New Session")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void testGetSessions_Success() throws Exception {
        // Pre-create a session
        ChatSession session = new ChatSession();
        session.setTitle("Old Session");
        session.setUser(doctorUser);
        session.setActive(true);
        chatSessionRepository.save(session);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/chat/sessions")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Old Session")));
    }

    @Test
    void testAskQuestion_Success() throws Exception {
        // Pre-create a session
        ChatSession session = new ChatSession();
        session.setTitle("Chat Session");
        session.setUser(doctorUser);
        session.setActive(true);
        session = chatSessionRepository.save(session);

        entityManager.flush();
        entityManager.clear();

        // Mock AI gateway routing to CLARIFICATION
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.CLARIFICATION, null, null, null, null, "Could you specify the timeframe?");
        when(aiChatGateway.route(anyString(), anyString(), anyString())).thenReturn(decision);

        ChatQuestionRequest request = new ChatQuestionRequest(session.getId(), "How many cases today?");

        mockMvc.perform(post("/chat/ask")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route", is("CLARIFICATION")))
                .andExpect(jsonPath("$.answer", is("Could you specify the timeframe?")));
    }
}
