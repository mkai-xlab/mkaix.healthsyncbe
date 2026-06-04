package com.g93.be.service;

import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;

public interface DoctorService {
    DoctorResponse createDoctor(CreateDoctorRequest request);
    java.util.List<DoctorResponse> getAllDoctors();
    java.util.List<DoctorResponse> getActiveDoctors();
    void softDeleteDoctor(Long id);
    void activateDoctor(Long id);
    DoctorResponse editDoctor(Long id, com.g93.be.dto.EditDoctorRequest request);
}
