package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for resetting the password using an OTP.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Token cannot be blank")
        @Size(min = 6, max = 6, message = "Token must be exactly 6 digits")
        String token,

        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, message = "New password must be at least 6 characters")
        String newPassword
) {
}
