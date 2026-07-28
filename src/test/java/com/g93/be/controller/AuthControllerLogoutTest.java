package com.g93.be.controller;

import com.g93.be.dto.LogoutRequest;
import com.g93.be.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerLogoutTest {

    @Test
    void logoutRevokesBearerAndRefreshTokens() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        Principal principal = () -> "doctor.one";

        ResponseEntity<Void> response = controller.logout(
                "Bearer access_token",
                new LogoutRequest("refresh_token"),
                principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).logout("access_token", "refresh_token", "doctor.one");
    }

    @Test
    void logoutRejectsMissingBearerToken() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);

        assertThrows(IllegalArgumentException.class,
                () -> controller.logout("invalid", new LogoutRequest("refresh_token"), () -> "doctor.one"));
    }
}
