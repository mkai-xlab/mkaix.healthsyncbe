package com.g93.be.service.impl;

import com.g93.be.dto.NotificationDto;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.entity.Notification;
import com.g93.be.entity.User;
import com.g93.be.mapper.NotificationMapper;
import com.g93.be.repository.NotificationRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void sendNotification(SendNotificationRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.userId()));

        // 1. Create and save entity
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setType(request.type());
        notification.setIsRead(false);
        
        Notification saved = notificationRepository.save(notification);
        
        // 2. Map to DTO
        NotificationDto dto = notificationMapper.toDto(saved);
        
        // 3. Send via WebSocket using STOMP. The destination will be /user/{username}/queue/notifications
        log.info("Sending STOMP notification to user {}", user.getUsername());
        messagingTemplate.convertAndSendToUser(
                user.getUsername(),
                "/queue/notifications",
                dto
        );
    }

    @Override
    public List<NotificationDto> getUnreadNotifications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                
        return notificationRepository.findByUserIdAndIsReadFalse(user.getId())
                .stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, String username) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
                
        if (!notification.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("Not authorized to modify this notification");
        }
        
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Notification {} marked as read by user {}", notificationId, username);
    }
}
