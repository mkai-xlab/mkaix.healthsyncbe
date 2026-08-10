package com.g93.be.service;

import com.g93.be.entity.User;
import com.g93.be.exception.LoginLockedException;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    private LoginAttemptService loginAttemptService;
    private User user;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(userRepository);
        user = new User();
        user.setUsername("doctor.one");
        user.setFailedLoginAttempts(0);
    }

    @Test
    void recordFailedAttempt_IncrementsCounterBeforeThreshold() {
        user.setFailedLoginAttempts(3);
        when(userRepository.findByLoginIdentifierForUpdate("doctor.one"))
                .thenReturn(Optional.of(user));

        Optional<LocalDateTime> lockedUntil = loginAttemptService.recordFailedAttempt("doctor.one");

        assertTrue(lockedUntil.isEmpty());
        assertEquals(4, user.getFailedLoginAttempts());
        assertNull(user.getLoginLockedUntil());
        verify(userRepository).save(user);
    }

    @Test
    void recordFailedAttempt_LocksAccountOnFifthFailure() {
        user.setFailedLoginAttempts(4);
        when(userRepository.findByLoginIdentifierForUpdate("doctor.one"))
                .thenReturn(Optional.of(user));

        Optional<LocalDateTime> lockedUntil = loginAttemptService.recordFailedAttempt("doctor.one");

        assertTrue(lockedUntil.isPresent());
        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.getLoginLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(userRepository).save(user);
    }

    @Test
    void ensureLoginAllowed_RejectsAccountDuringLockPeriod() {
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(10);
        user.setFailedLoginAttempts(5);
        user.setLoginLockedUntil(lockedUntil);
        when(userRepository.findByLoginIdentifierForUpdate("doctor.one"))
                .thenReturn(Optional.of(user));

        LoginLockedException exception = assertThrows(
                LoginLockedException.class,
                () -> loginAttemptService.ensureLoginAllowed("doctor.one"));

        assertEquals(lockedUntil, exception.getLockedUntil());
        verify(userRepository, never()).save(user);
    }

    @Test
    void ensureLoginAllowed_ClearsExpiredLock() {
        user.setFailedLoginAttempts(5);
        user.setLoginLockedUntil(LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByLoginIdentifierForUpdate("doctor.one"))
                .thenReturn(Optional.of(user));

        loginAttemptService.ensureLoginAllowed("doctor.one");

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLoginLockedUntil());
        verify(userRepository).save(user);
    }

    @Test
    void resetFailedAttempts_ClearsCounterAndLock() {
        user.setFailedLoginAttempts(5);
        user.setLoginLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByLoginIdentifierForUpdate("doctor.one"))
                .thenReturn(Optional.of(user));

        loginAttemptService.resetFailedAttempts("doctor.one");

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLoginLockedUntil());
        verify(userRepository).save(user);
    }
}
