package com.g93.be.service;

import com.g93.be.entity.User;
import com.g93.be.exception.LoginLockedException;
import com.g93.be.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void ensureLoginAllowed(String identifier) {
        userRepository.findByLoginIdentifierForUpdate(identifier).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            if (isLocked(user, now)) {
                throw new LoginLockedException(user.getLoginLockedUntil());
            }
            if (user.getLoginLockedUntil() != null) {
                clearAttempts(user);
                userRepository.save(user);
            }
        });
    }

    @Transactional
    public Optional<LocalDateTime> recordFailedAttempt(String identifier) {
        Optional<User> existingUser = userRepository.findByLoginIdentifierForUpdate(identifier);
        if (existingUser.isEmpty()) {
            return Optional.empty();
        }

        User user = existingUser.get();
        LocalDateTime now = LocalDateTime.now();
        if (isLocked(user, now)) {
            return Optional.of(user.getLoginLockedUntil());
        }
        if (user.getLoginLockedUntil() != null) {
            clearAttempts(user);
        }

        int failedAttempts = currentAttempts(user) + 1;
        user.setFailedLoginAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setFailedLoginAttempts(MAX_FAILED_ATTEMPTS);
            user.setLoginLockedUntil(now.plus(LOCK_DURATION));
        }
        userRepository.save(user);
        return Optional.ofNullable(user.getLoginLockedUntil());
    }

    @Transactional
    public void resetFailedAttempts(String identifier) {
        userRepository.findByLoginIdentifierForUpdate(identifier).ifPresent(user -> {
            if (currentAttempts(user) > 0 || user.getLoginLockedUntil() != null) {
                clearAttempts(user);
                userRepository.save(user);
            }
        });
    }

    private boolean isLocked(User user, LocalDateTime now) {
        return user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(now);
    }

    private int currentAttempts(User user) {
        return user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
    }

    private void clearAttempts(User user) {
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
    }
}
