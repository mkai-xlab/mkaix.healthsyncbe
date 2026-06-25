package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for editing an existing doctor's profile.
 * Partial updates are permitted; fields can be null if they are not being updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditDoctorRequest {
    private String fullName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private String phone;
    private String avatarUrl;
    private Integer yearsOfExperience;
}
