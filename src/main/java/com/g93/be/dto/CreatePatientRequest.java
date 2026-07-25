package com.g93.be.dto;


import com.g93.be.entity.Gender;
import com.g93.be.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Data Transfer Object for creating a new patient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePatientRequest {
    @NotBlank(message = "Patient code cannot be blank")
    private String patientCode;
    
    @NotBlank(message = "Full name cannot be blank")
    @jakarta.validation.constraints.Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;
    
    private LocalDate dateOfBirth;
    private Gender gender;
    @jakarta.validation.constraints.Pattern(regexp = "^\\d+$", message = "Phone must contain only numbers")
    @jakarta.validation.constraints.Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;
    @jakarta.validation.constraints.Email(message = "Invalid email format")
    @jakarta.validation.constraints.Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
}
