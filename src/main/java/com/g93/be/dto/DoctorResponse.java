package com.g93.be.dto;

import com.g93.be.entity.DoctorPosition;
import com.g93.be.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private UserStatus status;
    private String doctorCode;
    private String licenseNumber;
    private String specialization;
    private String hospitalName;
    private Integer yearsOfExperience;
    private String academicTitle;
    private String degree;
    private String signatureUrl;
    private String bio;
    private DoctorPosition position;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
