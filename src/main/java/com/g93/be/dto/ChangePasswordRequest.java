package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for changing a user's password.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Old password cannot be blank")
        String oldPassword,

        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, message = "New password must be at least 6 characters")
        String newPassword
) {
}
