package com.g93.be.service;

import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.BusinessQueryIntent;
import com.g93.be.chat.BusinessQueryResult;
import com.g93.be.chat.ChatRoute;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.chat.MedicalRetrievalResult;
import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatSourceResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AiChatGateway aiGateway;
    @Mock
    private BusinessDataQueryService businessDataQueryService;
    @Mock
    private MedicalRagService medicalRagService;

    private ChatOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new ChatOrchestratorService(
                userRepository, aiGateway, businessDataQueryService, medicalRagService);
    }

    @Test
    void businessQuestionUsesControlledBusinessFlowOnly() {
        User doctor = user(7L, "DOCTOR");
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.BUSINESS_DATA, BusinessQueryIntent.TODAY_EXAMINATION_COUNT,
                null, null, null, null);
        BusinessQueryResult data = new BusinessQueryResult("examination_count=3", List.of(
                new ChatSourceResponse("db", "Examinations", "BUSINESS_DATA",
                        "database:examinations", null)));
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(aiGateway.route("How many examinations today?", "DOCTOR")).thenReturn(decision);
        when(businessDataQueryService.execute(decision, "doctor")).thenReturn(data);
        when(aiGateway.answerBusiness("How many examinations today?", data.context()))
                .thenReturn("There are 3 examinations today.");

        ChatAnswerResponse response = service.ask("How many examinations today?", "doctor");

        assertEquals("BUSINESS_DATA", response.route());
        assertEquals(1, response.sources().size());
        verify(medicalRagService, never()).retrieve(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void medicalQuestionUsesOwnerAwareRetrievalAndReturnsWarning() {
        User doctor = user(7L, "DOCTOR");
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.MEDICAL_RAG, BusinessQueryIntent.UNKNOWN, null, null, null, null);
        MedicalRetrievalResult retrieval = new MedicalRetrievalResult("KL grade evidence", List.of(
                new ChatSourceResponse("guideline", "OA guideline", "FILE",
                        "knowledge-document:1", 0.92)));
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(aiGateway.route("Explain KL grade 3", "DOCTOR")).thenReturn(decision);
        when(medicalRagService.retrieve("Explain KL grade 3", "DOCTOR", 7L))
                .thenReturn(retrieval);
        when(aiGateway.answerMedical("Explain KL grade 3", retrieval.context()))
                .thenReturn("KL grade 3 indicates definite joint-space narrowing.");

        ChatAnswerResponse response = service.ask("Explain KL grade 3", "doctor");

        assertEquals("MEDICAL_RAG", response.route());
        assertNotNull(response.warning());
        verify(businessDataQueryService, never()).execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingMedicalEvidenceDoesNotAskGeminiToInventAnswer() {
        User doctor = user(7L, "DOCTOR");
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.MEDICAL_RAG, BusinessQueryIntent.UNKNOWN, null, null, null, null);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(aiGateway.route("Unknown medical topic", "DOCTOR")).thenReturn(decision);
        when(medicalRagService.retrieve("Unknown medical topic", "DOCTOR", 7L))
                .thenReturn(new MedicalRetrievalResult("", List.of()));

        ChatAnswerResponse response = service.ask("Unknown medical topic", "doctor");

        assertEquals(0, response.sources().size());
        verify(aiGateway, never()).answerMedical(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private User user(Long id, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setUsername("doctor");
        user.setRole(role);
        return user;
    }
}
