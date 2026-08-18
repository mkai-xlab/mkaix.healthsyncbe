package com.g93.be.service;

import com.g93.be.chat.AiChatGateway;
import com.g93.be.chat.BusinessQueryResult;
import com.g93.be.chat.ChatRoute;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.chat.GeneratedChatAnswer;
import com.g93.be.chat.MedicalRetrievalResult;
import com.g93.be.dto.ChatAnswerResponse;
import com.g93.be.dto.ChatSourceResponse;
import com.g93.be.entity.ChatMessage;
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

    private final AiChatGateway aiGateway;
    private final BusinessDataQueryService businessDataQueryService;
    private final MedicalRagService medicalRagService;
    private final ChatSessionService chatSessionService;

    public ChatAnswerResponse ask(Long sessionId, String question, String username) {
        ChatSessionService.PreparedConversation conversation =
                chatSessionService.prepare(sessionId, question, username);
        String roleCode = conversation.user().getRole().getCode();
        String history = conversation.history();
        ChatRoutingDecision decision = normalize(aiGateway.route(question, roleCode, history));

        AnswerResult result = switch (decision.route()) {
            case CLARIFICATION -> new AnswerResult(
                    new GeneratedChatAnswer(clarification(decision), null), List.of(), null);
            case BUSINESS_DATA -> businessAnswer(question, username, decision, history);
            case MEDICAL_RAG -> medicalAnswer(question, roleCode, conversation.user().getId(), history);
            case HYBRID -> hybridAnswer(
                    question, username, roleCode, conversation.user().getId(), decision, history);
        };
        ChatMessage savedMessage = chatSessionService.saveAssistantMessage(
                conversation.session(), decision.route().name(), result.answer());
        return response(
                conversation.session().getId(),
                savedMessage.getId(),
                decision.route(),
                result.answer(),
                result.sources(),
                result.warning());
    }

    private AnswerResult businessAnswer(
            String question,
            String username,
            ChatRoutingDecision decision,
            String history) {
        BusinessQueryResult result = businessDataQueryService.execute(decision, username);
        return new AnswerResult(
                aiGateway.answerBusiness(question, result.context(), history), result.sources(), null);
    }

    private AnswerResult medicalAnswer(String question, String roleCode, Long userId, String history) {
        MedicalRetrievalResult result = medicalRagService.retrieve(
                contextualRetrievalQuery(question, history), roleCode, userId);
        if (result.isEmpty()) {
            return new AnswerResult(new GeneratedChatAnswer(
                    "I could not find sufficient approved medical evidence in the knowledge base.", null),
                    List.of(), MEDICAL_WARNING);
        }
        return new AnswerResult(
                aiGateway.answerMedical(question, result.context(), history), result.sources(), MEDICAL_WARNING);
    }

    private AnswerResult hybridAnswer(
            String question,
            String username,
            String roleCode,
            Long userId,
            ChatRoutingDecision decision,
            String history) {
        BusinessQueryResult business = businessDataQueryService.execute(decision, username);
        MedicalRetrievalResult medical = medicalRagService.retrieve(
                contextualRetrievalQuery(question + "\nHEALTHSYNC DATA:\n" + business.context(), history),
                roleCode, userId);
        if (medical.isEmpty()) {
            return new AnswerResult(
                    aiGateway.answerBusiness(question, business.context(), history),
                    business.sources(), MEDICAL_WARNING);
        }
        List<ChatSourceResponse> sources = new ArrayList<>(business.sources());
        sources.addAll(medical.sources());
        return new AnswerResult(
                aiGateway.answerHybrid(question, business.context(), medical.context(), history),
                sources, MEDICAL_WARNING);
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

    private String contextualRetrievalQuery(String question, String history) {
        if (history == null || history.isBlank()) {
            return question;
        }
        int start = Math.max(0, history.length() - 2_000);
        return history.substring(start) + "\nCURRENT QUESTION: " + question;
    }

    private ChatAnswerResponse response(
            Long sessionId,
            Long messageId,
            ChatRoute route,
            GeneratedChatAnswer answer,
            List<ChatSourceResponse> sources,
            String warning) {
        return new ChatAnswerResponse(
                sessionId,
                messageId,
                route.name(),
                answer.content(),
                List.copyOf(sources),
                warning,
                LocalDateTime.now(),
                answer.tokensUsed());
    }

    private record AnswerResult(
            GeneratedChatAnswer answer,
            List<ChatSourceResponse> sources,
            String warning) {
    }
}
