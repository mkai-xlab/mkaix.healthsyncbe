package com.g93.be.chat;

/**
 * Structured AI assessment used to decide whether an uploaded source contains
 * substantive medical or healthcare knowledge.
 */
public record MedicalDocumentAssessment(
        Boolean medical,
        Double confidence,
        String reason) {
}
