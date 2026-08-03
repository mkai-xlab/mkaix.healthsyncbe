package com.g93.be.service.impl;
import com.g93.be.dto.PatientGradeStatsDto;



import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.entity.User;
import com.g93.be.exception.UnauthorizedAccessException;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getAllExaminations(Pageable pageable, String username, Boolean isPersonal) {
        Page<Examination> examinationPage = examinationRepository.findAll(getCustomSortPageable(pageable));
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
    public ExaminationDto getExaminationById(Long id, String username) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));

        if (user.getRole() != null && "DOCTOR".equalsIgnoreCase(user.getRole().getCode())) {
            if (examination.getDoctor() == null || !examination.getDoctor().getId().equals(user.getId())) {
                throw new UnauthorizedAccessException("BÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚ÂºÃƒâ€šÃ‚Â¡n khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´ng cÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â³ quyÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚Â»Ãƒâ€šÃ‚Ân truy cÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚ÂºÃƒâ€šÃ‚Â­p hÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚Â»ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ sÃƒÆ’Ã¢â‚¬Â Ãƒâ€šÃ‚Â¡ thuÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚Â»ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢c cÃƒÆ’Ã¢â‚¬Â Ãƒâ€šÃ‚Â¡ sÃƒÆ’Ã‚Â¡Ãƒâ€šÃ‚Â»Ãƒâ€¦Ã‚Â¸ nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â y.");
            }
        }

        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
        return examinationMapper.toDto(examination, instances);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findByDoctorId(doctorId, getCustomSortPageable(pageable));
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
        Page<Examination> examinationPage = examinationRepository.findByPatientId(patientId, getCustomSortPageable(pageable));
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
    public long getTotalExaminations(Long userId, Boolean isPersonal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            return examinationRepository.count();
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorId(userId);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalSevereExaminations(Long userId, Boolean isPersonal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        List<Integer> severeGrades = List.of(3, 4);
        String roleCode = user.getRole().getCode();
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByMaxPredictedGradeIn(severeGrades);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(userId, severeGrades);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalVerifiedExaminations(Long userId, Boolean isPersonal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        ExaminationStatus verifiedStatus = ExaminationStatus.VERIFIED;
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByStatus(verifiedStatus);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatus(userId, verifiedStatus);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalUnverifiedExaminations(Long userId, Boolean isPersonal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));
        
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        String roleCode = user.getRole().getCode();
        ExaminationStatus verifiedStatus = ExaminationStatus.VERIFIED;
        
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByStatusNot(verifiedStatus);
        } else if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatusNot(userId, verifiedStatus);
        }
        
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByStatus(ExaminationStatus status, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
                
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndStatus(user.getId(), status, pageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByStatus(status, pageable);
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

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
    public PageResponse<ExaminationDto> getExaminationsByGrade(Integer grade, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
                
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndMaxPredictedGrade(user.getId(), grade, pageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByMaxPredictedGrade(grade, pageable);
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

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
    public List<PatientGradeStatsDto> getPatientGradeStatistics(String username, Boolean isPersonal) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return List.of();
        }

        String roleCode = user.getRole().getCode();
        List<ExaminationRepository.GradePatientCountProjection> projections;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            projections = examinationRepository.countPatientsByLatestGradeForDoctor(user.getId());
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            projections = examinationRepository.countPatientsByLatestGrade();
        } else {
            return List.of();
        }

        return projections.stream()
                .map(p -> new PatientGradeStatsDto(p.getGrade(), p.getPatientCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsSortedByStudyDate(String direction, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }
        
        org.springframework.data.domain.Sort sort = "asc".equalsIgnoreCase(direction) ? 
                org.springframework.data.domain.Sort.by("studyDate").ascending() : 
                org.springframework.data.domain.Sort.by("studyDate").descending();
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findAll(sortedPageable);
        } else {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsSortedByUploadDate(String direction, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }
        
        org.springframework.data.domain.Sort sort = "asc".equalsIgnoreCase(direction) ? 
                org.springframework.data.domain.Sort.by("createdAt").ascending() : 
                org.springframework.data.domain.Sort.by("createdAt").descending();
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findAll(sortedPageable);
        } else {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsFilteredByStudyDate(java.time.LocalDate date, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndStudyDate(user.getId(), date, pageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByStudyDate(date, pageable);
        } else {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsFilteredByUploadDate(java.time.LocalDate date, String username, Boolean isPersonal, Pageable pageable) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User with username/email " + username + " not found"));
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = date.atTime(23, 59, 59);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndCreatedAtBetween(user.getId(), start, end, pageable);
        } else if ("DEPARTMENT_HEAD".equalsIgnoreCase(roleCode) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByCreatedAtBetween(start, end, pageable);
        } else {
            return new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByPatientIdAndStudyMonth(Long patientId, int year, int month, Pageable pageable) {
        java.time.LocalDate startDate = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        Page<Examination> examinationPage = examinationRepository.findByPatientIdAndStudyDateBetween(patientId, startDate, endDate, pageable);
        return mapToPageResponse(examinationPage);
    }

    private PageResponse<ExaminationDto> mapToPageResponse(Page<Examination> examinationPage) {
        java.util.List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    java.util.List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
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

    private Pageable getCustomSortPageable(Pageable pageable) {
        Sort sort = Sort.by(
            Sort.Order.desc("maxPredictedGrade").nullsLast(),
            Sort.Order.desc("createdAt")
        );
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}

