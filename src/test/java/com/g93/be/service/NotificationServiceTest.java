package com.g93.be.service;

import com.g93.be.dto.NotificationDto;
import com.g93.be.entity.Notification;
import com.g93.be.entity.User;
import com.g93.be.mapper.NotificationMapper;
import com.g93.be.repository.NotificationRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
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
