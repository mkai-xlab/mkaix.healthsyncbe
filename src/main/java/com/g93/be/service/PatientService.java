package com.g93.be.service;

import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PatientResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientFilterRequest;
import org.springframework.data.domain.Pageable;

public interface PatientService {
    PatientResponse createPatient(CreatePatientRequest request);
    PageResponse<PatientResponse> getAllPatients(PatientFilterRequest filter, Pageable pageable);
    void deletePatient(Long id);
    PatientResponse editPatient(Long id, EditPatientRequest request);
}
