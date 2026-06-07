package com.g93.be.controller;

import com.g93.be.dto.NotificationDto;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Retrieves all unread notifications for the currently authenticated user.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(username));
    }

    /**
     * Marks a specific notification as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        notificationService.markAsRead(id, username);
        return ResponseEntity.ok("Notification marked as read");
    }

    /**
     * Test endpoint to trigger a notification to a specific user.
     * In a real application, this might be restricted to ADMINs or internal services.
     */
    @PostMapping("/send")
    public ResponseEntity<String> sendTestNotification(@Valid @RequestBody SendNotificationRequest request) {
        log.info("Triggering test notification for user ID: {}", request.userId());
        notificationService.sendNotification(request);
        return ResponseEntity.ok("Notification sent successfully");
    }
}
