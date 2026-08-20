package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientUploadErrorDto {
    private String patientCode;
    private String patientName;
    private String errorMessage;
}
