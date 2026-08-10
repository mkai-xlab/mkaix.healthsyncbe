package com.g93.be.exception;

import java.time.LocalDateTime;

public class LoginLockedException extends RuntimeException {

    private final LocalDateTime lockedUntil;

    public LoginLockedException(LocalDateTime lockedUntil) {
        super("Too many failed login attempts. Login is locked until " + lockedUntil + ".");
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
