package com.g93.be.service;
import com.g93.be.dto.PatientGradeStatsDto;


import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.ExaminationStatus;
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
    PageResponse<ExaminationDto> getExaminationsByStatus(ExaminationStatus status, String username, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByGrade(Integer grade, String username, Pageable pageable);
    java.util.List<PatientGradeStatsDto> getPatientGradeStatistics(String username);
    PageResponse<ExaminationDto> getExaminationsSortedByStudyDate(String direction, String username, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsSortedByUploadDate(String direction, String username, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsFilteredByStudyDate(java.time.LocalDate date, String username, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsFilteredByUploadDate(java.time.LocalDate date, String username, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByPatientIdAndStudyMonth(Long patientId, int year, int month, Pageable pageable);
}

