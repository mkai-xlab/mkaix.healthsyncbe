package com.g93.be.mapper;

import com.g93.be.dto.DoctorResponse;
import com.g93.be.entity.Doctor;
import org.springframework.stereotype.Component;

/**
 * Mapper component for mapping Doctor entities to their corresponding DTOs.
 */
@Component
public class DoctorMapper {

    /**
     * Maps a Doctor entity to a DoctorResponse DTO.
     *
     * @param doctor The Doctor entity to map.
     * @return The mapped DoctorResponse DTO.
     */
    public DoctorResponse toResponse(Doctor doctor) {
        if (doctor == null) {
            return null;
        }
        
        DoctorResponse response = new DoctorResponse();
        response.setId(doctor.getId());
        response.setUsername(doctor.getUsername());
        response.setFullName(doctor.getFullName());
        response.setEmail(doctor.getEmail());
        response.setPhone(doctor.getPhone());
        response.setAvatarUrl(doctor.getAvatar() != null ? doctor.getAvatar().getS3BucketKey() : null);
        response.setRole(doctor.getRole() != null ? doctor.getRole().getName() : null);
        response.setStatus(doctor.getStatus());

        response.setYearsOfExperience(doctor.getYearsOfExperience());
        response.setCreatedAt(doctor.getCreatedAt());
        response.setUpdatedAt(doctor.getUpdatedAt());
        return response;
    }
}
