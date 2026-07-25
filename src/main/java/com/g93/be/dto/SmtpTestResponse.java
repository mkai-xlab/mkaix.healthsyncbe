package com.g93.be.dto;

/**
 * Response returned after an SMTP test email has been queued.
 *
 * @param status queue status, not a delivery confirmation
 * @param provider configured SMTP provider
 * @param recipient test email recipient
 */
public record SmtpTestResponse(
        String status,
        String provider,
        String recipient
) {
}
