package com.g93.be.controller;

import com.g93.be.dto.MarkAllNotificationsReadResponse;
import com.g93.be.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void markAllAsReadUsesAuthenticatedUserAndReturnsUpdatedCount() {
        when(authentication.getName()).thenReturn("admin");
        when(notificationService.markAllAsRead("admin")).thenReturn(4);

        ResponseEntity<MarkAllNotificationsReadResponse> response =
                notificationController.markAllAsRead(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(new MarkAllNotificationsReadResponse(4), response.getBody());
        verify(notificationService).markAllAsRead("admin");
    }
}
