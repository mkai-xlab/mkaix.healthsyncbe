package com.g93.be.service;

import com.g93.be.dto.NotificationDto;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.entity.Notification;
import com.g93.be.entity.User;
import com.g93.be.mapper.NotificationMapper;
import com.g93.be.repository.NotificationRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void sendNotificationPersistsUnreadNotificationAndPublishesWebSocketMessage() {
        User user = new User();
        user.setId(7L);
        user.setUsername("doctor");
        Notification saved = notification(15L, false);
        saved.setUser(user);
        NotificationDto savedDto = dto(saved);
        SendNotificationRequest request = new SendNotificationRequest(
                7L, "New result", "A result is ready", "RESULT", Map.of("examinationId", 12L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationMapper.toDto(saved)).thenReturn(savedDto);

        notificationService.sendNotification(request);

        ArgumentCaptor<Notification> persisted = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(persisted.capture());
        assertEquals(user, persisted.getValue().getUser());
        assertEquals(false, persisted.getValue().getIsRead());
        ArgumentCaptor<NotificationDto> payload = ArgumentCaptor.forClass(NotificationDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("doctor"), eq("/queue/notifications"), payload.capture());
        assertEquals(Map.of("examinationId", 12L), payload.getValue().data());
    }

    @Test
    void sendNotificationRejectsUnknownTargetUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> notificationService.sendNotification(new SendNotificationRequest(99L, "Title", "Message", "SYSTEM", null)));

        assertEquals("User not found with id: 99", error.getMessage());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void getUnreadNotificationsReturnsMappedUnreadNotifications() {
        User user = new User();
        user.setId(7L);
        Notification unread = notification(2L, false);
        NotificationDto unreadDto = dto(unread);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(7L)).thenReturn(List.of(unread));
        when(notificationMapper.toDto(unread)).thenReturn(unreadDto);

        assertEquals(List.of(unreadDto), notificationService.getUnreadNotifications("doctor"));
    }

    @Test
    void getUnreadNotificationsRejectsUnknownUser() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> notificationService.getUnreadNotifications("unknown"));

        verify(notificationRepository, never()).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void markAsReadUpdatesNotificationOwnedByCurrentUser() {
        User user = new User();
        user.setUsername("doctor");
        Notification unread = notification(3L, false);
        unread.setUser(user);
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(unread));

        notificationService.markAsRead(3L, "doctor");

        assertEquals(true, unread.getIsRead());
        assertEquals(false, unread.getReadAt() == null);
        verify(notificationRepository).save(unread);
    }

    @Test
    void markAsReadRejectsAnotherUsersNotification() {
        User owner = new User();
        owner.setUsername("other");
        Notification unread = notification(3L, false);
        unread.setUser(owner);
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(unread));

        assertThrows(IllegalArgumentException.class, () -> notificationService.markAsRead(3L, "doctor"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadRejectsUnknownNotification() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> notificationService.markAsRead(99L, "doctor"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void getAllNotificationsReturnsReadAndUnreadNewestFirst() {
        User user = new User();
        user.setId(7L);
        Notification unread = notification(2L, false);
        Notification read = notification(1L, true);
        NotificationDto unreadDto = dto(unread);
        NotificationDto readDto = dto(read);

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(unread, read));
        when(notificationMapper.toDto(unread)).thenReturn(unreadDto);
        when(notificationMapper.toDto(read)).thenReturn(readDto);

        List<NotificationDto> result = notificationService.getAllNotifications("doctor");

        assertEquals(List.of(unreadDto, readDto), result);
        assertEquals(List.of(false, true), result.stream().map(NotificationDto::isRead).toList());
    }

    @Test
    void getAllNotificationsRejectsUnknownUser() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> notificationService.getAllNotifications("unknown"));

        assertEquals("User not found", error.getMessage());
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void markAllAsReadUpdatesOnlyNotificationsOwnedByCurrentUser() {
        User user = new User();
        user.setId(7L);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(notificationRepository.markAllAsReadByUserId(eq(7L), any(LocalDateTime.class)))
                .thenReturn(3);

        int updatedCount = notificationService.markAllAsRead("doctor");

        assertEquals(3, updatedCount);
        verify(notificationRepository).markAllAsReadByUserId(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void markAllAsReadRejectsUnknownUserWithoutUpdatingNotifications() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> notificationService.markAllAsRead("unknown"));

        assertEquals("User not found", error.getMessage());
        verify(notificationRepository, never())
                .markAllAsReadByUserId(any(Long.class), any(LocalDateTime.class));
    }

    private Notification notification(Long id, boolean isRead) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setTitle("Notification " + id);
        notification.setMessage("Message " + id);
        notification.setType("SYSTEM");
        notification.setIsRead(isRead);
        notification.setCreatedAt(LocalDateTime.of(2026, 7, 24, 10, id.intValue()));
        return notification;
    }

    private NotificationDto dto(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getTitle(), notification.getMessage(),
                notification.getType(), notification.getIsRead(), notification.getCreatedAt(), null);
    }
}
