package com.g93.be.service.impl;

import org.springframework.security.access.AccessDeniedException;

import com.g93.be.entity.DicomInstance;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.entity.AuditLog;
import com.g93.be.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> getAllPatients(PatientFilterRequest filter, Pageable pageable, String username) {
        Long doctorId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.getRole() != null) {
                String role = user.getRole().getCode();
                Boolean isPersonal = filter.getIsPersonal();
                
                if ("DOCTOR".equals(role)) {
                    if (Boolean.FALSE.equals(isPersonal)) {
                        throw new AccessDeniedException("Bạn không có quyền xem toàn bộ danh sách bệnh nhân của hệ thống.");
                    }
                    doctorId = user.getId();
                } else if (Boolean.TRUE.equals(isPersonal)) {
                    doctorId = user.getId();
                }
            }
        }

        String keyword = null;
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            keyword = filter.getKeyword().trim();
            if (keyword.length() < 2) {
                throw new IllegalArgumentException("Từ khóa tìm kiếm phải từ 2 ký tự trở lên!");
            }
        }

        boolean hasStatuses = filter.getStatuses() != null && !filter.getStatuses().isEmpty();
        List<ExaminationStatus> statuses = new ArrayList<>();
        if (hasStatuses) {
            for (String s : filter.getStatuses()) {
                try {
                    statuses.add(ExaminationStatus.valueOf(s));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid examination status: {}", s);
                }
            }
        }
        if (statuses.isEmpty()) {
            hasStatuses = false;
        }

        boolean hasSeverities = filter.getSeverities() != null && !filter.getSeverities().isEmpty();
        List<Integer> severities = filter.getSeverities();

        Page<Patient> patientPage = patientRepository.findAllByCustomFilters(
                keyword,
                hasStatuses,
                hasStatuses ? statuses : null,
                hasSeverities,
                hasSeverities ? severities : null,
                doctorId,
                pageable);

        List<PatientResponse> content = patientPage.getContent().stream()
                .map(patientMapper::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                patientPage.getNumber(),
                patientPage.getSize(),
                patientPage.getTotalElements(),
                patientPage.getTotalPages(),
                patientPage.isLast());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePatient(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new IllegalArgumentException("Patient with id " + id + " not found");
        }
        patientRepository.deleteById(id);
        log.info("Deleted patient with id {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientResponse editPatient(Long id, EditPatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient with id " + id + " not found"));

        if (request.getFullName() != null)
            patient.setFullName(request.getFullName());
        if (request.getDateOfBirth() != null)
            patient.setDob(request.getDateOfBirth());
        if (request.getGender() != null)
            patient.setGender(request.getGender());
        if (request.getPhone() != null)
            patient.setPhone(request.getPhone());
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            patient.setEmail(request.getEmail());
        }

        Patient saved = patientRepository.save(patient);
        log.info("Edited patient with id {}", saved.getId());
        return patientMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientResponse createPatient(CreatePatientRequest request) {
        log.info("Starting registration for patient name: {}", request.getFullName());

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        Patient patient = new Patient();
        patient.setFullName(request.getFullName());
        patient.setDob(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        if (patient.getPatientCode() == null) {
            patient.setPatientCode("PAT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient saved successfully with ID: {}", savedPatient.getId());

        return patientMapper.toResponse(savedPatient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDetailsResponse getPatientDetailsWithImages(String patientId, String username) {
        Patient patient = patientRepository.findByPatientCode(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient with code " + patientId + " not found"));

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username).orElse(null);
        }
        
        if (user != null && user.getRole() != null && "DOCTOR".equals(user.getRole().getCode())) {
            boolean hasAccess = examinationRepository.existsByPatientIdAndDoctorId(patient.getId(), user.getId());
            if (!hasAccess) {
                throw new IllegalArgumentException("Bạn không có quyền truy cập hồ sơ thuộc cơ sở này.");
            }
        }

        if (user != null) {
            AuditLog logEntry = new AuditLog();
            logEntry.setUser(user);
            logEntry.setTitle("View Patient History");
            logEntry.setDescription("Viewed patient history for patient UUID: " + patient.getPatientCode());
            auditLogRepository.save(logEntry);
        }

        PatientDetailsResponse pdr = new PatientDetailsResponse();
        pdr.setPatient(patientMapper.toResponse(patient));

        List<Examination> examinations = examinationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
        List<ExaminationDto> examDtos = new ArrayList<>();

        String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                .build().toUriString();

        for (Examination ex : examinations) {
            ExaminationDto ed = new ExaminationDto();
            ed.setExaminationId(ex.getId());
            ed.setEncounterCode(ex.getEncounterCode());
            ed.setStatus(ex.getStatus().name());
            ed.setStudyDate(ex.getStudyDate());
            ed.setVisitTime(ex.getVisitTime());
            ed.setReferringPhysician(ex.getReferringPhysician());

            List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
            if (instances != null && !instances.isEmpty()) {
                ed.setThumbnailUrl(baseUrl + "/dicom/instances/" + instances.get(0).getId() + "/image");
                List<ExaminationImageDto> imageDtos = new ArrayList<>();
                for (DicomInstance instance : instances) {
                    ExaminationImageDto img = new ExaminationImageDto();
                    img.setExaminationId(ex.getId());
                    img.setEncounterCode(ex.getEncounterCode());
                    img.setStatus(ex.getStatus().name());
                    img.setVisitTime(ex.getVisitTime());
                    img.setImageUrl(baseUrl + "/dicom/instances/" + instance.getId() + "/image");
                    imageDtos.add(img);
                }
                ed.setImages(imageDtos);
            }
            examDtos.add(ed);
        }
        pdr.setRecentExaminations(examDtos);

        return pdr;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> getPatientsByUploadDate(LocalDate date, Pageable pageable, String username) {
        Long filterDoctorId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.getRole() != null && "DOCTOR".equals(user.getRole().getCode())) {
                filterDoctorId = user.getId();
            }
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();

        Page<Patient> patientPage = patientRepository.findPatientsByUploadDateAndDoctor(
                startOfDay, startOfNextDay, filterDoctorId, pageable);

        List<PatientResponse> content = patientPage.getContent().stream()
                .map(patientMapper::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                patientPage.getNumber(),
                patientPage.getSize(),
                patientPage.getTotalElements(),
                patientPage.getTotalPages(),
                patientPage.isLast());
    }
}
