package com.g93.be.service;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.ForgotPasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.dto.ResetPasswordRequest;
import com.g93.be.entity.PasswordResetToken;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.exception.FirstTimeLoginException;
import com.g93.be.exception.LoginLockedException;
import com.g93.be.repository.PasswordResetTokenRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import com.g93.be.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailUtil mailUtil;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private CustomUserDetails mockUserDetails;
    private PasswordResetToken mockToken;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setCode("DOCTOR");

        mockUser = new User();
        mockUser.setUsername("test_user");
        mockUser.setFullName("Test Doctor");
        mockUser.setEmail("test@hospital.com");
        mockUser.setPassword("encoded_password");
        mockUser.setRole(role);
        mockUser.setIsFirstActivated(false);

        mockUserDetails = new CustomUserDetails(mockUser, new ArrayList<>());

        mockToken = new PasswordResetToken();
        mockToken.setUser(mockUser);
        mockToken.setToken("123456");
        mockToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));
    }

    // --- LOGIN TESTS ---

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUserDetails);
        when(jwtTokenProvider.generateAccessToken(mockUserDetails)).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(mockUserDetails)).thenReturn("refresh_token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        assertEquals("refresh_token", response.refreshToken());
        assertEquals("test_user", response.username());
        assertEquals("Test Doctor", response.fullName());
        assertEquals("DOCTOR", response.role());
        verify(loginAttemptService).resetFailedAttempts("test_user");
    }

    @Test
    void login_WrongPassword_RecordsFailedAttempt() {
        LoginRequest request = new LoginRequest("test_user", "wrong_password");
        BadCredentialsException failure = new BadCredentialsException("Bad credentials");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(failure);
        when(loginAttemptService.recordFailedAttempt("test_user")).thenReturn(Optional.empty());

        BadCredentialsException thrown = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request));

        assertSame(failure, thrown);
        verify(loginAttemptService).ensureLoginAllowed("test_user");
        verify(loginAttemptService).recordFailedAttempt("test_user");
        verify(loginAttemptService, never()).resetFailedAttempts(anyString());
    }

    @Test
    void login_FifthWrongPassword_ThrowsLoginLockedException() {
        LoginRequest request = new LoginRequest("test_user", "wrong_password");
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(15);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        when(loginAttemptService.recordFailedAttempt("test_user"))
                .thenReturn(Optional.of(lockedUntil));

        LoginLockedException exception = assertThrows(
                LoginLockedException.class,
                () -> authService.login(request));

        assertEquals(lockedUntil, exception.getLockedUntil());
        verify(loginAttemptService, never()).resetFailedAttempts(anyString());
    }

    @Test
    void login_FirstTimeLogin_ThrowsException() {
        mockUser.setIsFirstActivated(true);
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUserDetails);

        assertThrows(FirstTimeLoginException.class, () -> authService.login(request));
        
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    @Test
    void logout_RevokesAccessAndRefreshTokens() {
        Duration accessTtl = Duration.ofMinutes(10);
        Duration refreshTtl = Duration.ofDays(6);
        when(jwtTokenProvider.isAccessTokenValid("access_token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshTokenValid("refresh_token")).thenReturn(true);
        when(jwtTokenProvider.extractUsernameFromAccessToken("access_token")).thenReturn("test_user");
        when(jwtTokenProvider.extractUsernameFromRefreshToken("refresh_token")).thenReturn("test_user");
        when(jwtTokenProvider.getAccessTokenRemainingValidity("access_token")).thenReturn(accessTtl);
        when(jwtTokenProvider.getRefreshTokenRemainingValidity("refresh_token")).thenReturn(refreshTtl);

        authService.logout("access_token", "refresh_token", "test_user");

        verify(tokenBlacklistService).blacklistAccessToken("access_token", accessTtl);
        verify(tokenBlacklistService).blacklistRefreshToken("refresh_token", refreshTtl);
    }

    @Test
    void logout_RejectsTokenFromAnotherUser() {
        when(jwtTokenProvider.isAccessTokenValid("access_token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshTokenValid("refresh_token")).thenReturn(true);
        when(jwtTokenProvider.extractUsernameFromAccessToken("access_token")).thenReturn("test_user");
        when(jwtTokenProvider.extractUsernameFromRefreshToken("refresh_token")).thenReturn("other_user");

        assertThrows(IllegalArgumentException.class,
                () -> authService.logout("access_token", "refresh_token", "test_user"));

        verifyNoInteractions(tokenBlacklistService);
    }

    // --- CHANGE PASSWORD TESTS ---

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("test_user", "old_password", "NewPassword@123");
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("old_password", "encoded_password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new_encoded_password");

        authService.changePassword(request);

        assertEquals("new_encoded_password", mockUser.getPassword());
        assertFalse(mockUser.getIsFirstActivated());
        assertEquals(0, mockUser.getFailedLoginAttempts());
        assertNull(mockUser.getLoginLockedUntil());
        verify(userRepository).save(mockUser);
    }

    @Test
    void changePassword_FirstTimeLogin_ActivatesAccount() {
        mockUser.setIsFirstActivated(true);
        ChangePasswordRequest request = new ChangePasswordRequest(
                "test_user", "temporary_password", "NewPassword@123");
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("temporary_password", "encoded_password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new_encoded_password");

        authService.changePassword(request);

        assertEquals("new_encoded_password", mockUser.getPassword());
        assertFalse(mockUser.getIsFirstActivated());
        verify(userRepository).save(mockUser);
    }

    @Test
    void changePassword_WrongOldPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("test_user", "wrong_old_password", "NewPassword@123");
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong_old_password", "encoded_password")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            authService.changePassword(request);
        });
        
        assertEquals("Invalid username or password", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_UserNotFound_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("unknown_user", "old_password", "NewPassword@123");
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.changePassword(request));
    }

    // --- FORGOT PASSWORD TESTS ---

    @Test
    void forgotPassword_UserNotFound_ReturnsEarly() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@hospital.com");
        when(userRepository.findByEmail("unknown@hospital.com")).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailUtil, never()).sendTemplateMail(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void forgotPassword_UserFound_GeneratesTokenAndSendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@hospital.com");
        when(userRepository.findByEmail("test@hospital.com")).thenReturn(Optional.of(mockUser));
        when(passwordResetTokenRepository.findByUser(mockUser)).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailUtil).sendTemplateMail(eq("test@hospital.com"), anyString(), eq("reset-password"), anyMap());
    }

    // --- RESET PASSWORD TESTS ---

    @Test
    void resetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@hospital.com", "123456", "NewPassword@123");
        when(userRepository.findByEmail("test@hospital.com")).thenReturn(Optional.of(mockUser));
        when(passwordResetTokenRepository.findByUser(mockUser)).thenReturn(Optional.of(mockToken));
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new_encoded_password");

        authService.resetPassword(request);

        assertEquals("new_encoded_password", mockUser.getPassword());
        assertFalse(mockUser.getIsFirstActivated());
        verify(userRepository).save(mockUser);
        verify(passwordResetTokenRepository).deleteByUser(mockUser);
    }

    @Test
    void resetPassword_UserNotFound_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("unknown@hospital.com", "123456", "NewPassword@123");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }

    @Test
    void resetPassword_TokenNotFound_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@hospital.com", "123456", "NewPassword@123");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(passwordResetTokenRepository.findByUser(mockUser)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }

    @Test
    void resetPassword_TokenMismatch_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@hospital.com", "wrong_token", "NewPassword@123");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(passwordResetTokenRepository.findByUser(mockUser)).thenReturn(Optional.of(mockToken));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }

    @Test
    void resetPassword_TokenExpired_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@hospital.com", "123456", "NewPassword@123");
        mockToken.setExpiryDate(LocalDateTime.now().minusMinutes(5)); // Expired
        
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(passwordResetTokenRepository.findByUser(mockUser)).thenReturn(Optional.of(mockToken));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }
}
