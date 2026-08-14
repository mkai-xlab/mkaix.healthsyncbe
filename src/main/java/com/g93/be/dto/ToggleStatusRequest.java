package com.g93.be.dto;

import lombok.Data;

/**
 * Data Transfer Object for toggling a user's status.
 * This is used when activating or deactivating a medical staff account.
 */
@Data
public class ToggleStatusRequest {
    
    /**
     * The reason for deactivating the user.
     * This field is required when the status is being changed to INACTIVE.
     */
    private String inactiveReason;
}
