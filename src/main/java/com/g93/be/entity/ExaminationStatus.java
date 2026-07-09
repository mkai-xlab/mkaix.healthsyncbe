package com.g93.be.entity;

/**
 * Enum representing the status of an examination.
 */
public enum ExaminationStatus {
    CREATED,
    PENDING_REVIEW,
    AI_PROCESSING,
    AI_COMPLETED,
    REVIEWED,
    REPORT_GENERATED,
    NEED_VERIFY,
    NEED_REVERIFY,
    CANCELLED
}
