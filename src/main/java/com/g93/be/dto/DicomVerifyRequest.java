package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DicomVerifyRequest {
    private String uploadSessionId;
    private List<String> acceptedPatientCodes;
}
