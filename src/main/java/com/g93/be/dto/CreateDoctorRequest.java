package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng")
    private String fullName;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;
    
    @NotBlank(message = "Phone number cannot be blank")
    @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
    private String phone;
    
    private String avatarUrl;
    
    private Integer yearsOfExperience;

    @Size(max = 100, message = "Degree must not exceed 100 characters")
    private String degree;

    private String biography;
}
