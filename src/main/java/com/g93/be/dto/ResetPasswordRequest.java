package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        @Size(min = 8, max = 32, message = "New password must be between 8 and 32 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,32}$",
                message = "New password must contain an uppercase letter, a number, and a special character")
        String newPassword
) {
}
