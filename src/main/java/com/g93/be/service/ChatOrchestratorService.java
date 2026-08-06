package com.g93.be.service;

import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.BusinessQueryResult;
import com.g93.be.chat.ChatRoute;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.chat.MedicalRetrievalResult;
import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatSourceResponse;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatOrchestratorService {

    private static final String MEDICAL_WARNING =
            "AI-generated medical information is for decision support and must be reviewed by a qualified clinician.";

    private final UserRepository userRepository;
    private final AiChatGateway aiGateway;
    private final BusinessDataQueryService businessDataQueryService;
    private final MedicalRagService medicalRagService;

    public ChatAnswerResponse ask(String question, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String roleCode = user.getRole().getCode();
        ChatRoutingDecision decision = normalize(aiGateway.route(question, roleCode));

        return switch (decision.route()) {
            case CLARIFICATION -> response(decision.route(), clarification(decision), List.of(), null);
            case BUSINESS_DATA -> businessAnswer(question, username, decision);
            case MEDICAL_RAG -> medicalAnswer(question, roleCode, user.getId());
            case HYBRID -> hybridAnswer(question, username, roleCode, user.getId(), decision);
        };
    }

    private ChatAnswerResponse businessAnswer(String question, String username, ChatRoutingDecision decision) {
        BusinessQueryResult result = businessDataQueryService.execute(decision, username);
        return response(decision.route(), aiGateway.answerBusiness(question, result.context()), result.sources(), null);
    }

    private ChatAnswerResponse medicalAnswer(String question, String roleCode, Long userId) {
        MedicalRetrievalResult result = medicalRagService.retrieve(question, roleCode, userId);
        if (result.isEmpty()) {
            return response(ChatRoute.MEDICAL_RAG,
                    "I could not find sufficient approved medical evidence in the knowledge base.",
                    List.of(), MEDICAL_WARNING);
        }
        return response(ChatRoute.MEDICAL_RAG,
                aiGateway.answerMedical(question, result.context()), result.sources(), MEDICAL_WARNING);
    }

    private ChatAnswerResponse hybridAnswer(
            String question, String username, String roleCode, Long userId, ChatRoutingDecision decision) {
        BusinessQueryResult business = businessDataQueryService.execute(decision, username);
        MedicalRetrievalResult medical = medicalRagService.retrieve(question, roleCode, userId);
        if (medical.isEmpty()) {
            return response(ChatRoute.HYBRID,
                    aiGateway.answerBusiness(question, business.context()), business.sources(), MEDICAL_WARNING);
        }
        List<ChatSourceResponse> sources = new ArrayList<>(business.sources());
        sources.addAll(medical.sources());
        return response(ChatRoute.HYBRID,
                aiGateway.answerHybrid(question, business.context(), medical.context()), sources, MEDICAL_WARNING);
    }

    private ChatRoutingDecision normalize(ChatRoutingDecision decision) {
        if (decision == null || decision.route() == null) {
            return new ChatRoutingDecision(ChatRoute.CLARIFICATION, null, null, null, null,
                    "Could you clarify whether you need HealthSync data or medical information?");
        }
        return decision;
    }

    private String clarification(ChatRoutingDecision decision) {
        return decision.clarificationQuestion() == null || decision.clarificationQuestion().isBlank()
                ? "Could you provide more detail about the information you need?"
                : decision.clarificationQuestion();
    }

    private ChatAnswerResponse response(
            ChatRoute route, String answer, List<ChatSourceResponse> sources, String warning) {
        return new ChatAnswerResponse(route.name(), answer, List.copyOf(sources), warning, LocalDateTime.now());
    }
}
