package com.g93.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditDoctorProfileRequest {
    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private Integer yearsOfExperience;

    @Size(max = 100, message = "Degree must not exceed 100 characters")
    private String degree;

    private String biography;
}
