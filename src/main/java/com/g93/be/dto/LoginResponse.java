package com.g93.be.dto;

import java.util.List;

/**
 * Data Transfer Object for successful login response.
 * Contains tokens and the authenticated user's client-facing profile data.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String role,
    String username,
    String fullName,
    List<PermissionResponse> permissions
) {}
