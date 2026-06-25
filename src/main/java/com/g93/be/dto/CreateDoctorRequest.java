package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for creating a new doctor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorRequest {
    @NotBlank(message = "Full name cannot be blank")
    @jakarta.validation.constraints.Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    @jakarta.validation.constraints.Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;
    
    @NotBlank(message = "Phone number cannot be blank")
    @jakarta.validation.constraints.Pattern(regexp = "^\\d+$", message = "Phone must contain only numbers")
    @jakarta.validation.constraints.Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;
    
    private String avatarUrl;
    
    private Integer yearsOfExperience;
}
