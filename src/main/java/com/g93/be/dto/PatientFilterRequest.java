package com.g93.be.dto;

import com.g93.be.entity.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Size;

/**
 * DTO for filtering patients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientFilterRequest {
    @Size(max = 100, message = "Từ khóa tìm kiếm không được quá 100 ký tự!")
    private String keyword;
    
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    
    private List<String> statuses;
    private List<Integer> severities;
}
