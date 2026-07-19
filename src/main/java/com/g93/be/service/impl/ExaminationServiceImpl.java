package com.g93.be.service.impl;


import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.entity.User;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.g93.be.mapper.ExaminationMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExaminationServiceImpl implements ExaminationService {

    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ExaminationMapper examinationMapper;
    private final com.g93.be.repository.UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getAllExaminations(Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findAll(pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public ExaminationDto getExaminationById(Long id) {
        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));
        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
        return examinationMapper.toDto(examination, instances);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findByDoctorId(doctorId, pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByPatientId(Long patientId, Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findByPatientId(patientId, pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast()
        );
    }

    @Override
    @Transactional
    public void markAsViewed(Long id) {
        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));
        examination.setIsViewed(1);
        examinationRepository.save(examination);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalExaminations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)) {
            return examinationRepository.count();
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorId(userId);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalSevereExaminations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        List<Integer> severeGrades = List.of(3, 4);
        String roleCode = user.getRole().getCode();
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByMaxPredictedGradeIn(severeGrades);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(userId, severeGrades);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalVerifiedExaminations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        ExaminationStatus verifiedStatus = ExaminationStatus.VERIFIED;
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByStatus(verifiedStatus);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatus(userId, verifiedStatus);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalUnverifiedExaminations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        ExaminationStatus verifiedStatus = ExaminationStatus.VERIFIED;
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByStatusNot(verifiedStatus);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatusNot(userId, verifiedStatus);
        }
        
        return 0L;
    }
}
