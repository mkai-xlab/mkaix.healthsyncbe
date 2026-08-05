package com.g93.be.service;
import com.g93.be.dto.PatientGradeStatsDto;


import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.ExaminationStatus;
import org.springframework.data.domain.Pageable;

public interface ExaminationService {
    PageResponse<ExaminationDto> getAllExaminations(Pageable pageable, String username, Boolean isPersonal);
    ExaminationDto getExaminationById(Long id, String username);
    PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByPatientId(Long patientId, Pageable pageable);
    void markAsViewed(Long id);
    long getTotalExaminations(Long userId, Boolean isPersonal);
    long getTotalSevereExaminations(Long userId, Boolean isPersonal);
    long getTotalVerifiedExaminations(Long userId, Boolean isPersonal);
    long getTotalUnverifiedExaminations(Long userId, Boolean isPersonal);
    long getTotalExaminationsInLast7Days(Long userId, Boolean isPersonal);
    PageResponse<ExaminationDto> getExaminationsByStatus(ExaminationStatus status, String username, Boolean isPersonal, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByGrade(Integer grade, String username, Boolean isPersonal, Pageable pageable);
    java.util.List<PatientGradeStatsDto> getPatientGradeStatistics(String username, Boolean isPersonal);
    PageResponse<ExaminationDto> getExaminationsSortedByStudyDate(String direction, String username, Boolean isPersonal, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsSortedByUploadDate(String direction, String username, Boolean isPersonal, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsFilteredByStudyDate(java.time.LocalDate date, String username, Boolean isPersonal, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsFilteredByUploadDate(java.time.LocalDate date, String username, Boolean isPersonal, Pageable pageable);
    PageResponse<ExaminationDto> getExaminationsByPatientIdAndStudyMonth(Long patientId, int year, int month, Pageable pageable);
}

