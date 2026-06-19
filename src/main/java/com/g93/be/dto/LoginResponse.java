package com.g93.be.dto;

import java.util.List;

/**
 * Data Transfer Object for successful login response.
 * Contains access token, refresh token, role, and username.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String role,
    String username,
    List<String> permissions
) {}
