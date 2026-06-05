package com.g93.be.service;

import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;

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
}
