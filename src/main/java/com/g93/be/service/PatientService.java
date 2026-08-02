package com.g93.be.service;
import com.g93.be.dto.PatientDetailsResponse;


import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PatientResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientFilterRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface PatientService {
    PatientResponse createPatient(CreatePatientRequest request);
    PageResponse<PatientResponse> getAllPatients(PatientFilterRequest filter, Pageable pageable, String username);
    void deletePatient(Long id);
    PatientResponse editPatient(Long id, EditPatientRequest request);
    PatientDetailsResponse getPatientDetailsWithImages(String patientId, String username);
    PageResponse<PatientResponse> getPatientsByUploadDate(LocalDate date, Pageable pageable, String username);
}

