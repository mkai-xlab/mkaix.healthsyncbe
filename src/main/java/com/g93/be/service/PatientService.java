package com.g93.be.service;

import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PatientResponse;
import java.util.List;

public interface PatientService {
    PatientResponse createPatient(CreatePatientRequest request);
    List<PatientResponse> getAllPatients();
    void deletePatient(Long id);
    PatientResponse editPatient(Long id, EditPatientRequest request);
}
