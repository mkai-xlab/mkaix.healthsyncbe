package com.g93.be.dto;

import com.g93.be.entity.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for filtering patients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientFilterRequest {
    private String patientCode;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
}
