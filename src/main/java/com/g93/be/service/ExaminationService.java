package com.g93.be.service;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ExaminationService {
    PageResponse<ExaminationDto> getAllExaminations(Pageable pageable);
    ExaminationDto getExaminationById(Long id);
    PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable);
}
