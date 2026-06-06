package com.g93.be.dto;

import com.g93.be.entity.UserRole;

/**
 * Data Transfer Object for successful login response.
 * Contains access token, refresh token, role, and username.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    UserRole role,
    String username
) {}
