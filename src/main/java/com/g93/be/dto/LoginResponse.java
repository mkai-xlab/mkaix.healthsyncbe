package com.g93.be.dto;

import com.g93.be.entity.UserRole;

/**
 * DTO trả về thông tin đăng nhập thành công.
 * Bao gồm access token, refresh token, role và username.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    UserRole role,
    String username
) {}
