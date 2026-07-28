package com.g93.be.controller;

import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.ForgotPasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.dto.LogoutRequest;
import com.g93.be.dto.ResetPasswordRequest;
import com.g93.be.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

import java.security.Principal;

/**
 * Controller for handling authentication-related REST API requests.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint for user login.
     * 
     * @param request The login request containing username and password.
     * @return ResponseEntity containing token information, role, and username.
     */
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Perform login via AuthService
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request,
            Principal principal) {
        authService.logout(
                extractBearerToken(authorizationHeader),
                request.refreshToken(),
                principal.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer access token is required");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Bearer access token is required");
        }
        return token;
    }

    /**
     * Endpoint for changing user password.
     * 
     * @param request The change password request containing old and new passwords.
     * @return A success message.
     */
    @PostMapping("/change-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    /**
     * Endpoint to initiate the forgot password flow.
     * Generates an OTP and sends it via email.
     * 
     * @param request The forgot password request containing the email.
     * @return A success message.
     */
    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("If the email exists, a password reset token has been sent.");
    }

    /**
     * Endpoint to reset the password using the OTP.
     * 
     * @param request The reset password request containing email, token, and new password.
     * @return A success message.
     */
    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password reset successfully");
    }
}
