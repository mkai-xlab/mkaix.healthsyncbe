package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for user login.
 * The username field corresponds to the registered email or username.
 */
public record LoginRequest(
    @NotBlank(message = "Username cannot be blank")
    String username,
    
    @NotBlank(message = "Password cannot be blank")
    String password
) {}
