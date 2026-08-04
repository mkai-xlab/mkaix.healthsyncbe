package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng")
    private String fullName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
    private String phone;
    private String avatarUrl;
    private Integer yearsOfExperience;

    @Size(max = 100, message = "Degree must not exceed 100 characters")
    private String degree;

    private String biography;
}
