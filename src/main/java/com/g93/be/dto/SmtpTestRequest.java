package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request for queuing an SMTP connectivity test email.
 *
 * @param recipient email address that should receive the test message
 */
public record SmtpTestRequest(
        @NotBlank(message = "Recipient cannot be blank")
        @Email(message = "Invalid recipient email format")
        String recipient
) {
}
