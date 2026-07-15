package com.g93.be.service;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ExaminationService {
    PageResponse<ExaminationDto> getAllExaminations(Pageable pageable);
    ExaminationDto getExaminationById(Long id);
    PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByPatientId(Long patientId, Pageable pageable);
    void markAsViewed(Long id);
    long getTotalExaminations(Long userId);
    long getTotalSevereExaminations(Long userId);
    long getTotalVerifiedExaminations(Long userId);
    long getTotalUnverifiedExaminations(Long userId);
}
