package com.g93.be.dto;

import com.g93.be.entity.DoctorPosition;
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
    private String fullName;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Phone number cannot be blank")
    private String phone;
    
    private String avatarUrl;
    
    @NotBlank(message = "Doctor code cannot be blank")
    private String doctorCode;
    
    @NotBlank(message = "License number cannot be blank")
    private String licenseNumber;
    
    @NotBlank(message = "Specialization cannot be blank")
    private String specialization;
    
    @NotBlank(message = "Hospital name cannot be blank")
    private String hospitalName;
    
    private Integer yearsOfExperience;
    private String academicTitle;
    private String degree;
    private String signatureUrl;
    private String bio;
    private DoctorPosition position;
}
