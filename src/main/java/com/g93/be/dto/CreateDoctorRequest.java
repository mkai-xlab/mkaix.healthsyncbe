package com.g93.be.dto;

import com.g93.be.entity.DoctorPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorRequest {
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
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
}
