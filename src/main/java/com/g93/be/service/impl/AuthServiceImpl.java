package com.g93.be.service.impl;

import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.exception.FirstTimeLoginException;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.g93.be.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Implementation of AuthService for handling user authentication.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticates a user and generates access/refresh tokens.
     * 
     * @param request Contains username (email) and password.
     * @return LoginResponse containing tokens and user details.
     * @throws FirstTimeLoginException If the user is logging in for the first time.
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. Authenticate user credentials via AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // 2. Get user details upon successful authentication
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 3. Check if it's the user's first login (requires password change)
        if (Boolean.TRUE.equals(userDetails.getUser().getIsFirstActivated())) {
            throw new FirstTimeLoginException("Account not activated or requires password change on first login.");
        }

        // 4. Generate Access and Refresh Tokens
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // 5. Return full login information to the client
        return new LoginResponse(
                accessToken,
                refreshToken,
                userDetails.getUser().getRole(),
                userDetails.getUsername()
        );
    }
}
