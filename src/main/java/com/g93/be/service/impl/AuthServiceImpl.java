package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.ForgotPasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.dto.ResetPasswordRequest;
import com.g93.be.entity.PasswordResetToken;
import com.g93.be.entity.User;
import com.g93.be.exception.FirstTimeLoginException;
import com.g93.be.repository.PasswordResetTokenRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.g93.be.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Implementation of AuthService for handling user authentication.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailUtil mailUtil;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordResetTokenRepository passwordResetTokenRepository, MailUtil mailUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailUtil = mailUtil;
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

    /**
     * Generates a 6-digit OTP and sends it to the user's email.
     * The OTP is valid for 10 minutes.
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            // Return to prevent email enumeration
            return;
        }

        // Delete existing token if any
        passwordResetTokenRepository.deleteByUser(user);

        // Generate 6-digit OTP
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(1000000);
        String token = String.format("%06d", num);

        // Save token
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        passwordResetTokenRepository.save(resetToken);

        // Send email
        Map<String, Object> variables = Map.of("token", token);
        mailUtil.sendTemplateMail(user.getEmail(), "Đặt lại mật khẩu - HealthSync", "reset-password", variables);
    }

    /**
     * Verifies the OTP and resets the password if valid.
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or token"));

        PasswordResetToken resetToken = passwordResetTokenRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or token"));

        // Check if token matches
        if (!resetToken.getToken().equals(request.token())) {
            throw new IllegalArgumentException("Invalid email or token");
        }

        // Check expiry
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token has expired. Please request a new one.");
        }

        // Token valid, update password
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        
        // If first time login, activate account
        if (Boolean.TRUE.equals(user.getIsFirstActivated())) {
            user.setIsFirstActivated(false);
        }
        userRepository.save(user);

        // Delete token after successful use
        passwordResetTokenRepository.deleteByUser(user);
    }
}
