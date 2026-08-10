package com.g93.be.service;

import com.g93.be.chat.GeneratedChatAnswer;
import com.g93.be.dto.ChatMessageResponse;
import com.g93.be.dto.ChatSessionResponse;
import com.g93.be.dto.CreateChatSessionRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.UpdateChatSessionRequest;
import com.g93.be.entity.ChatMessage;
import com.g93.be.entity.ChatMessageRole;
import com.g93.be.entity.ChatSession;
import com.g93.be.entity.Examination;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.ChatMessageRepository;
import com.g93.be.repository.ChatSessionRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatSessionService {

    private static final String DEFAULT_TITLE = "New conversation";
    private static final int MAX_HISTORY_CHARACTERS = 12_000;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ExaminationRepository examinationRepository;

    @Transactional
    public ChatSessionResponse create(CreateChatSessionRequest request, String username) {
        User user = requireUser(username);
        ChatSession session = createSession(user, request == null ? null : request.title(),
                request == null ? null : request.examinationId());
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatSessionResponse> getSessions(String username, Pageable pageable) {
        User user = requireUser(username);
        return PageResponse.of(chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(user.getId(), pageable)
                .map(this::toSessionResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessages(Long sessionId, String username, Pageable pageable) {
        ChatSession session = requireOwnedSession(sessionId, requireUser(username));
        Page<ChatMessageResponse> messages = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAscIdAsc(session.getId(), pageable)
                .map(this::toMessageResponse);
        return PageResponse.of(messages);
    }

    @Transactional
    public ChatSessionResponse update(Long sessionId, UpdateChatSessionRequest request, String username) {
        if (request == null || (request.title() == null && request.active() == null)) {
            throw new IllegalArgumentException("At least one session field must be provided");
        }
        ChatSession session = requireOwnedSession(sessionId, requireUser(username));
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("Session title must not be blank");
            }
            session.setTitle(title);
        }
        if (request.active() != null) {
            session.setActive(request.active());
        }
        session.setUpdatedAt(LocalDateTime.now());
        return toSessionResponse(chatSessionRepository.save(session));
    }

    @Transactional
    public PreparedConversation prepare(Long sessionId, String question, String username) {
        User user = requireUser(username);
        ChatSession session = sessionId == null
                ? createSession(user, titleFromQuestion(question), null)
                : requireOwnedSession(sessionId, user);
        if (!session.isActive()) {
            throw new IllegalArgumentException("Chat session is inactive");
        }

        String history = conversationContext(session, chatMessageRepository
                .findTop20BySessionIdOrderByCreatedAtDescIdDesc(session.getId()));
        saveMessage(session, ChatMessageRole.USER, question.trim(), null, null);
        if (DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(titleFromQuestion(question));
        }
        touch(session);
        return new PreparedConversation(session, user, history);
    }

    @Transactional
    public ChatMessage saveAssistantMessage(
            ChatSession session,
            String route,
            GeneratedChatAnswer answer) {
        ChatMessage message = saveMessage(
                session,
                ChatMessageRole.ASSISTANT,
                answer.content(),
                route,
                answer.tokensUsed());
        touch(session);
        return message;
    }

    private ChatSession createSession(User user, String title, Long examinationId) {
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(normalizeTitle(title));
        session.setActive(true);
        if (examinationId != null) {
            Examination examination = examinationRepository.findById(examinationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Examination not found"));
            authorizeExamination(user, examination);
            session.setExamination(examination);
        }
        return chatSessionRepository.save(session);
    }

    private ChatMessage saveMessage(
            ChatSession session,
            ChatMessageRole role,
            String content,
            String route,
            Integer tokensUsed) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setRoute(route);
        message.setTokensUsed(tokensUsed);
        return chatMessageRepository.save(message);
    }

    private void touch(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ChatSession requireOwnedSession(Long sessionId, User user) {
        return chatSessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private void authorizeExamination(User user, Examination examination) {
        String roleCode = user.getRole() == null ? null : user.getRole().getCode();
        boolean supervisor = "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)
                || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode);
        boolean assignedDoctor = examination.getDoctor() != null
                && Objects.equals(examination.getDoctor().getId(), user.getId());
        if (!supervisor && !assignedDoctor) {
            throw new AccessDeniedException("User cannot create a chat session for this examination");
        }
    }

    private String formatHistory(List<ChatMessage> newestFirst) {
        if (newestFirst == null || newestFirst.isEmpty()) {
            return "";
        }
        List<ChatMessage> selected = new ArrayList<>();
        int characters = 0;
        for (ChatMessage message : newestFirst) {
            int messageLength = message.getContent() == null ? 0 : message.getContent().length();
            if (!selected.isEmpty() && characters + messageLength > MAX_HISTORY_CHARACTERS) {
                break;
            }
            selected.add(message);
            characters += messageLength;
        }
        Collections.reverse(selected);
        StringBuilder history = new StringBuilder();
        for (ChatMessage message : selected) {
            history.append(message.getRole().name())
                    .append(": ")
                    .append(message.getContent())
                    .append('\n');
        }
        return history.toString().trim();
    }

    private String conversationContext(ChatSession session, List<ChatMessage> messages) {
        String history = formatHistory(messages);
        if (session.getExamination() == null) {
            return history;
        }
        String examinationContext = "SYSTEM: This conversation is linked to examination ID "
                + session.getExamination().getId() + ".";
        return history.isBlank() ? examinationContext : examinationContext + "\n" + history;
    }

    private String normalizeTitle(String title) {
        return title == null || title.isBlank() ? DEFAULT_TITLE : truncateTitle(title.trim());
    }

    private String titleFromQuestion(String question) {
        String normalized = question == null ? DEFAULT_TITLE : question.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? DEFAULT_TITLE : truncateTitle(normalized);
    }

    private String truncateTitle(String title) {
        return title.length() <= 160 ? title : title.substring(0, 157) + "...";
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getExamination() == null ? null : session.getExamination().getId(),
                session.getTitle(),
                session.isActive(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getRole().name(),
                message.getContent(),
                message.getRoute(),
                message.getTokensUsed(),
                message.getCreatedAt());
    }

    public record PreparedConversation(ChatSession session, User user, String history) {
    }
}
