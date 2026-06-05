package com.g93.be.mapper;

import com.g93.be.dto.PatientResponse;
import com.g93.be.entity.Patient;
import org.springframework.stereotype.Component;

/**
 * Mapper component for mapping Patient entities to their corresponding DTOs.
 */
@Component
public class PatientMapper {

    /**
     * Maps a Patient entity to a PatientResponse DTO.
     *
     * @param patient The Patient entity to map.
     * @return The mapped PatientResponse DTO.
     */
    public PatientResponse toResponse(Patient patient) {
        if (patient == null) {
            return null;
        }

        return new PatientResponse(
                patient.getId(),
                patient.getPatientCode(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }
}
