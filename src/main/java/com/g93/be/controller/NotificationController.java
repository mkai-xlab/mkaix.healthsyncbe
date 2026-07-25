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
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Retrieves all notifications for the currently authenticated user.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> getAllNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getAllNotifications(authentication.getName()));
    }

    /**
     * Retrieves all unread notifications for the currently authenticated user.
     */
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(username));
    }

    /**
     * Marks a specific notification as read.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> sendTestNotification(@Valid @RequestBody SendNotificationRequest request) {
        log.info("Triggering test notification for user ID: {}", request.userId());
        notificationService.sendNotification(request);
        return ResponseEntity.ok("Notification sent successfully");
    }
}
