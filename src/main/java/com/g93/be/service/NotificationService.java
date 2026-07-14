package com.g93.be.service;

import com.g93.be.dto.NotificationDto;
import com.g93.be.dto.SendNotificationRequest;

import java.util.List;

public interface NotificationService {
    
    /**
     * Creates a new notification in the database and sends it via STOMP to the target user.
     * 
     * @param request The notification details including target user ID.
     */
    void sendNotification(SendNotificationRequest request);

    /**
     * Retrieves all unread notifications for a specific user.
     * 
     * @param username The username of the user.
     * @return List of unread notifications.
     */
    List<NotificationDto> getUnreadNotifications(String username);

    /**
     * Marks a specific notification as read.
     * 
     * @param notificationId The ID of the notification.
     * @param username The username of the user (to verify ownership).
     */
    void markAsRead(Long notificationId, String username);
}
