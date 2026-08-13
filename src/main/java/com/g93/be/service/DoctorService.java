package com.g93.be.service;

import com.g93.be.dto.EditDoctorRequest;
import com.g93.be.dto.EditDoctorProfileRequest;

import com.g93.be.entity.UserStatus;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DoctorService {
    DoctorResponse createDoctor(CreateDoctorRequest request);

    PageResponse<DoctorResponse> searchDoctors(String keyword, String specialization, UserStatus status,
            Pageable pageable);

    List<DoctorResponse> getAllDoctors();

    List<DoctorResponse> getActiveDoctors();

    void softDeleteDoctor(Long id, String reason);

    void activateDoctor(Long id);

    DoctorResponse editDoctor(Long id, EditDoctorRequest request);

    DoctorResponse getDoctorProfile(String username);

    DoctorResponse editDoctorProfile(String username, EditDoctorProfileRequest request);

    DoctorResponse updateDoctorAvatar(String username, MultipartFile file);
}
