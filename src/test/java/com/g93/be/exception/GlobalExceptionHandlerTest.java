package com.g93.be.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void loginLocked_ReturnsLockedResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        LoginLockedException exception = new LoginLockedException(LocalDateTime.now().plusMinutes(15));

        ResponseEntity<ErrorResponse> response = handler.handleLoginLockedException(exception);

        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.LOCKED.value(), response.getBody().getStatus());
        assertEquals("LOGIN_TEMPORARILY_LOCKED", response.getBody().getError());
    }

    @Test
    void aiProviderQuotaExceeded_ReturnsTooManyRequests() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception exception = new RuntimeException(
                "Failed to generate content",
                new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded for generate content"));

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(exception);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getBody().getStatus());
        assertEquals("AI_PROVIDER_QUOTA_EXCEEDED", response.getBody().getError());
        assertEquals(
                "AI provider quota has been exceeded. Please try again after the quota resets.",
                response.getBody().getMessage());
    }
}
