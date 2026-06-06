package com.g93.be.service.impl;

import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.entity.User;
import com.g93.be.exception.FirstTimeLoginException;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.g93.be.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Implementation of AuthService for handling user authentication.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Changes the user's password. Requires verification of the old password.
     * 
     * @param request Contains username, old password, and new password.
     */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        // 1. Find user by username
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        // 2. Verify old password
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        // 3. Update password
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        
        // 4. If first time login, activate account
        if (Boolean.TRUE.equals(user.getIsFirstActivated())) {
            user.setIsFirstActivated(false);
        }
        
        userRepository.save(user);
    }
}
