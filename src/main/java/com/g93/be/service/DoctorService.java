package com.g93.be.service;

import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.UserStatus;
import org.springframework.data.domain.Pageable;

public interface DoctorService {
    DoctorResponse createDoctor(CreateDoctorRequest request);
    PageResponse<DoctorResponse> searchDoctors(String keyword, String specialization, UserStatus status, Pageable pageable);
    java.util.List<DoctorResponse> getAllDoctors();
    java.util.List<DoctorResponse> getActiveDoctors();
    void softDeleteDoctor(Long id);
    void activateDoctor(Long id);
    DoctorResponse editDoctor(Long id, com.g93.be.dto.EditDoctorRequest request);
}
