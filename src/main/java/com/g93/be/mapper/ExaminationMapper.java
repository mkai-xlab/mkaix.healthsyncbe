package com.g93.be.mapper;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.ExaminationImageDto;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper component for mapping Examination entities to their corresponding DTOs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExaminationMapper {

    private final PatientMapper patientMapper;

    /**
     * Maps an Examination entity to an ExaminationDto.
     * Includes patient mapping and image mapping if dicom instances are provided.
     *
     * @param ex The Examination entity to map.
     * @param instances The list of associated DicomInstance entities.
     * @return The mapped ExaminationDto.
     */
    public ExaminationDto toDto(Examination ex, List<DicomInstance> instances) {
        if (ex == null) {
            return null;
        }

        ExaminationDto ed = new ExaminationDto();
        ed.setExaminationId(ex.getId());
        ed.setEncounterCode(ex.getEncounterCode());
        ed.setStatus(ex.getStatus() != null ? ex.getStatus().name() : null);
        ed.setStudyDate(ex.getStudyDate());
        ed.setVisitTime(ex.getVisitTime());
        ed.setBodyPart(ex.getBodyPart());
        ed.setReferringPhysician(ex.getReferringPhysician());

        if (ex.getPatient() != null) {
            ed.setPatient(patientMapper.toResponse(ex.getPatient()));
        }

        String baseUrl = "";
        try {
            baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception e) {
            log.warn("Could not determine base URL from request context: {}", e.getMessage());
        }

        if (instances != null && !instances.isEmpty()) {
            if (!baseUrl.isEmpty()) {
                ed.setThumbnailUrl(baseUrl + "/dicom/instances/" + instances.get(0).getId() + "/image");
            }
            List<ExaminationImageDto> imageDtos = new ArrayList<>();
            for (DicomInstance instance : instances) {
                ExaminationImageDto img = new ExaminationImageDto();
                img.setExaminationId(ex.getId());
                img.setEncounterCode(ex.getEncounterCode());
                img.setStatus(ex.getStatus() != null ? ex.getStatus().name() : null);
                img.setVisitTime(ex.getVisitTime());
                if (!baseUrl.isEmpty()) {
                    img.setImageUrl(baseUrl + "/dicom/instances/" + instance.getId() + "/image");
                }
                imageDtos.add(img);
            }
            ed.setImages(imageDtos);
        }

        return ed;
    }
}
