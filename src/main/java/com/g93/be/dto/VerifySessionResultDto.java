package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifySessionResultDto {
    private List<Long> savedInstanceIds;
    private List<PatientUploadErrorDto> failedPatients;
}
