package com.g93.be.service.impl;

import com.g93.be.dto.PatientGradeStatsDto;
import java.time.LocalDateTime;
import java.time.LocalDate;

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
        log.info("Fetching all examinations for username: {}, isPersonal: {}", username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorId(user.getId(), getCustomSortPageable(pageable));
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorId(user.getId(), getCustomSortPageable(pageable));
            } else {
                examinationPage = examinationRepository.findAll(getCustomSortPageable(pageable));
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findAll(getCustomSortPageable(pageable));
            }
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
                examinationPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public ExaminationDto getExaminationById(Long id, String username) {
        log.info("Fetching examination by id: {} for username: {}", id, username);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));

        if (user.getRole() != null && "DOCTOR".equalsIgnoreCase(user.getRole().getCode())) {
            if (examination.getDoctor() == null || !examination.getDoctor().getId().equals(user.getId())) {
                log.warn("Unauthorized access by user: {} to examination: {}", user.getUsername(), examination.getId());
                throw new UnauthorizedAccessException("Bạn không có quyền truy cập hồ sơ thuộc cơ sở này.");
            }
        }

        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
        return examinationMapper.toDto(examination, instances);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable) {
        log.info("Fetching examinations for doctor id: {} pageable: {}", doctorId, pageable);
        Page<Examination> examinationPage = examinationRepository.findByDoctorId(doctorId,
                getCustomSortPageable(pageable));
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
    public PageResponse<ExaminationDto> getExaminationsByPatientId(Long patientId, Pageable pageable) {
        log.info("Fetching examinations for patient id: {} pageable: {}", patientId, pageable);
        Page<Examination> examinationPage = examinationRepository.findByPatientId(patientId,
                getCustomSortPageable(pageable));
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
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorId(userId);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return examinationRepository.countByDoctorId(userId);
            } else {
                return examinationRepository.count();
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return 0L; // Admin doesn't have personal exams
            } else {
                return examinationRepository.count();
            }
        }

        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalExaminationsInLast7Days(Long userId, Boolean isPersonal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with id " + userId + " not found"));

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return 0L;
        }

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        String roleCode = user.getRole().getCode();

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndCreatedAtAfter(userId, sevenDaysAgo);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return examinationRepository.countByDoctorIdAndCreatedAtAfter(userId, sevenDaysAgo);
            } else {
                return examinationRepository.countByCreatedAtAfter(sevenDaysAgo);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return 0L; // Admin doesn't have personal exams
            } else {
                return examinationRepository.countByCreatedAtAfter(sevenDaysAgo);
            }
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

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(userId, severeGrades);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(userId, severeGrades);
            } else {
                return examinationRepository.countByMaxPredictedGradeIn(severeGrades);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return 0L; // Admin doesn't have personal exams
            } else {
                return examinationRepository.countByMaxPredictedGradeIn(severeGrades);
            }
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

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatus(userId, verifiedStatus);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return examinationRepository.countByDoctorIdAndStatus(userId, verifiedStatus);
            } else {
                return examinationRepository.countByStatus(verifiedStatus);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return 0L; // Admin doesn't have personal exams
            } else {
                return examinationRepository.countByStatus(verifiedStatus);
            }
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

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            return examinationRepository.countByDoctorIdAndStatusNot(userId, verifiedStatus);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return examinationRepository.countByDoctorIdAndStatusNot(userId, verifiedStatus);
            } else {
                return examinationRepository.countByStatusNot(verifiedStatus);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return 0L; // Admin doesn't have personal exams
            } else {
                return examinationRepository.countByStatusNot(verifiedStatus);
            }
        }

        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByStatus(ExaminationStatus status, String username,
            Boolean isPersonal, Pageable pageable) {
        log.info("Fetching examinations by status: {} for username: {}, isPersonal: {}", status, username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndStatus(user.getId(), status, pageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorIdAndStatus(user.getId(), status, pageable);
            } else {
                examinationPage = examinationRepository.findByStatus(status, pageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findByStatus(status, pageable);
            }
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
                examinationPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByGrade(Integer grade, String username, Boolean isPersonal,
            Pageable pageable) {
        log.info("Fetching examinations by grade: {} for username: {}, isPersonal: {}", grade, username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndMaxPredictedGrade(user.getId(), grade, pageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorIdAndMaxPredictedGrade(user.getId(), grade,
                        pageable);
            } else {
                examinationPage = examinationRepository.findByMaxPredictedGrade(grade, pageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findByMaxPredictedGrade(grade, pageable);
            }
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
                examinationPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientGradeStatsDto> getPatientGradeStatistics(String username, Boolean isPersonal) {
        log.info("Fetching patient grade statistics for username: {}, isPersonal: {}", username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });

        if (user.getRole() == null || user.getRole().getCode() == null) {
            return List.of();
        }

        String roleCode = user.getRole().getCode();
        List<ExaminationRepository.GradePatientCountProjection> projections;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            projections = examinationRepository.countPatientsByLatestGradeForDoctor(user.getId());
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                projections = examinationRepository.countPatientsByLatestGradeForDoctor(user.getId());
            } else {
                projections = examinationRepository.countPatientsByLatestGrade();
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return List.of(); // Admin doesn't have personal exams
            } else {
                projections = examinationRepository.countPatientsByLatestGrade();
            }
        } else {
            return List.of();
        }

        return projections.stream()
                .map(p -> new PatientGradeStatsDto(p.getGrade(), p.getPatientCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsSortedByStudyDate(String direction, String username,
            Boolean isPersonal, Pageable pageable) {
        log.info("Sorting examinations by study date direction: {} for username: {}, isPersonal: {}", direction,
                username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by("studyDate").ascending()
                : Sort.by("studyDate").descending();
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
            } else {
                examinationPage = examinationRepository.findAll(sortedPageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findAll(sortedPageable);
            }
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsSortedByUploadDate(String direction, String username,
            Boolean isPersonal, Pageable pageable) {
        log.info("Sorting examinations by upload date direction: {} for username: {}, isPersonal: {}", direction,
                username, isPersonal);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorId(user.getId(), sortedPageable);
            } else {
                examinationPage = examinationRepository.findAll(sortedPageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findAll(sortedPageable);
            }
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsFilteredByStudyDate(LocalDate date, String username,
            Boolean isPersonal, Pageable pageable) {
        log.info("Filtering examinations by study date: {} for username: {}, isPersonal: {}", date, username,
                isPersonal);
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Study date cannot be in the future");
        }
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndStudyDate(user.getId(), date, pageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorIdAndStudyDate(user.getId(), date, pageable);
            } else {
                examinationPage = examinationRepository.findByStudyDate(date, pageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findByStudyDate(date, pageable);
            }
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsFilteredByUploadDate(LocalDate date, String username,
            Boolean isPersonal, Pageable pageable) {
        log.info("Filtering examinations by upload date: {} for username: {}, isPersonal: {}", date, username,
                isPersonal);
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Upload date cannot be in the future");
        }
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("User with username/email {} not found", username);
                    return new IllegalArgumentException("User with username/email " + username + " not found");
                });
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);

        String roleCode = user.getRole().getCode();
        Page<Examination> examinationPage;
        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            examinationPage = examinationRepository.findByDoctorIdAndCreatedAtBetween(user.getId(), start, end,
                    pageable);
        } else if ("HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode) || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                examinationPage = examinationRepository.findByDoctorIdAndCreatedAtBetween(user.getId(), start, end,
                        pageable);
            } else {
                examinationPage = examinationRepository.findByCreatedAtBetween(start, end, pageable);
            }
        } else if ("ADMIN".equalsIgnoreCase(roleCode)) {
            if (Boolean.TRUE.equals(isPersonal)) {
                return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true); // Admin doesn't have
                                                                                             // personal exams
            } else {
                examinationPage = examinationRepository.findByCreatedAtBetween(start, end, pageable);
            }
        } else {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, true);
        }

        return mapToPageResponse(examinationPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByPatientIdAndStudyMonth(Long patientId, int year, int month,
            Pageable pageable) {
        log.info("Fetching examinations for patient id: {} for year: {}, month: {}", patientId, year, month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Study month cannot be in the future");
        }
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        Page<Examination> examinationPage = examinationRepository.findByPatientIdAndStudyDateBetween(patientId,
                startDate, endDate, pageable);
        return mapToPageResponse(examinationPage);
    }

    private PageResponse<ExaminationDto> mapToPageResponse(Page<Examination> examinationPage) {
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

    private Pageable getCustomSortPageable(Pageable pageable) {
        Sort sort = Sort.by(
                Sort.Order.desc("maxPredictedGrade").nullsLast(),
                Sort.Order.desc("createdAt"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
