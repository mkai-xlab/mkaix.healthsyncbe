package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for user login.
 * The username field corresponds to the registered email or username.
 */
public record LoginRequest(
    @NotBlank(message = "Username cannot be blank")
    String username,
    
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    String password
) {
    public LoginRequest {
        if (username != null) {
            username = username.trim();
        }
    }
}
