package com.g93.be.service;

import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.ForgotPasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.dto.ResetPasswordRequest;

/**
 * Service interface for authentication-related operations.
 */
public interface AuthService {
    
    /**
     * Authenticates a user based on login credentials.
     *
     * @param request The login request containing username and password.
     * @return LoginResponse containing access and refresh tokens.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Changes the password for a user.
     *
     * @param request The change password request.
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * Initiates the forgot password flow by generating a 6-digit OTP and sending it via email.
     *
     * @param request Contains the user's email.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Resets the password using the 6-digit OTP sent via email.
     *
     * @param request Contains email, token, and new password.
     */
    void resetPassword(ResetPasswordRequest request);
}
