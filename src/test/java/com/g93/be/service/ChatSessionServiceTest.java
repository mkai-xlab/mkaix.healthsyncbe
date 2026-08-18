package com.g93.be.service;

import com.g93.be.dto.CreateChatSessionRequest;
import com.g93.be.entity.ChatMessage;
import com.g93.be.entity.ChatMessageRole;
import com.g93.be.entity.ChatSession;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.ChatMessageRepository;
import com.g93.be.repository.ChatSessionRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExaminationRepository examinationRepository;

    private ChatSessionService service;
    private User doctor;

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(
                chatSessionRepository,
                chatMessageRepository,
                userRepository,
                examinationRepository);
        doctor = user(7L, "DOCTOR");
    }

    @Test
    void createSessionCanLinkAssignedExamination() {
        Examination examination = examination(42L, 7L);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(examinationRepository.findById(42L)).thenReturn(Optional.of(examination));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(12L);
            return session;
        });

        var response = service.create(new CreateChatSessionRequest("Knee follow-up", 42L), "doctor");

        assertEquals(12L, response.id());
        assertEquals(42L, response.examinationId());
        assertEquals("Knee follow-up", response.title());
        assertTrue(response.active());
    }

    @Test
    void createSessionRejectsUnassignedExamination() {
        Examination examination = examination(42L, 99L);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(examinationRepository.findById(42L)).thenReturn(Optional.of(examination));

        assertThrows(AccessDeniedException.class,
                () -> service.create(new CreateChatSessionRequest("Private case", 42L), "doctor"));

        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    void firstQuestionAutomaticallyCreatesTitledSession() {
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(12L);
            return session;
        });
        when(chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDescIdDesc(12L))
                .thenReturn(List.of());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionService.PreparedConversation prepared =
                service.prepare(null, "  Explain   KL grade 3  ", "doctor");

        assertEquals(12L, prepared.session().getId());
        assertEquals("Explain KL grade 3", prepared.session().getTitle());
        assertEquals("", prepared.history());
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertEquals("Explain   KL grade 3", messageCaptor.getValue().getContent());
    }

    @Test
    void preparePersistsQuestionAndBuildsOrderedHistory() {
        ChatSession session = session(doctor, true);
        ChatMessage newest = message(session, ChatMessageRole.ASSISTANT, "Previous answer");
        ChatMessage oldest = message(session, ChatMessageRole.USER, "Previous question");
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(chatSessionRepository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDescIdDesc(12L))
                .thenReturn(List.of(newest, oldest));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(session)).thenReturn(session);

        ChatSessionService.PreparedConversation prepared =
                service.prepare(12L, "Follow-up question", "doctor");

        assertEquals("USER: Previous question\nASSISTANT: Previous answer", prepared.history());
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertEquals(ChatMessageRole.USER, messageCaptor.getValue().getRole());
        assertEquals("Follow-up question", messageCaptor.getValue().getContent());
    }

    @Test
    void inactiveSessionRejectsNewQuestion() {
        ChatSession session = session(doctor, false);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(chatSessionRepository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(session));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.prepare(12L, "New question", "doctor"));

        assertEquals("Chat session is inactive", error.getMessage());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void anotherUsersSessionIsNotExposed() {
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(chatSessionRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.prepare(99L, "Question", "doctor"));
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

    private Examination examination(Long id, Long doctorId) {
        Doctor assigned = new Doctor();
        assigned.setId(doctorId);
        Examination examination = new Examination();
        examination.setId(id);
        examination.setDoctor(assigned);
        return examination;
    }

    private ChatSession session(User user, boolean active) {
        ChatSession session = new ChatSession();
        session.setId(12L);
        session.setUser(user);
        session.setTitle("Existing session");
        session.setActive(active);
        return session;
    }

    private ChatMessage message(ChatSession session, ChatMessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
