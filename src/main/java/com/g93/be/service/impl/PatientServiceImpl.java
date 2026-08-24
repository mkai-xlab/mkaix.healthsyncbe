package com.g93.be.service.impl;

import org.springframework.security.access.AccessDeniedException;

import com.g93.be.entity.DicomInstance;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.service.KnowledgeIngestionService;
import com.g93.be.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final DiagnosisReviewRepository diagnosisReviewRepository;
    private final ReportRepository reportRepository;
    private final ImageRepository imageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    // Empty when app.chat.enabled=false, since KnowledgeIngestionService (and the
    // VectorStore it depends on) only exist as beans when RAG/chat is enabled.
    private final Optional<KnowledgeIngestionService> knowledgeIngestionService;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Value("${app.pdf.export-dir}")
    private String reportExportDir;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> getAllPatients(PatientFilterRequest filter, Pageable pageable,
            String username) {
        Long doctorId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.getRole() != null) {
                String role = user.getRole().getCode();
                Boolean isPersonal = filter.getIsPersonal();

                if (!"ADMIN".equals(role) && !"DEPARTMENT_HEAD".equals(role) && !"HEAD_OF_DEPARTMENT".equals(role) && !"DOCTOR".equals(role)) {
                    throw new AccessDeniedException("Bạn không có quyền truy cập.");
                }

                if ("DOCTOR".equals(role)) {
                    if (Boolean.FALSE.equals(isPersonal)) {
                        throw new AccessDeniedException(
                                "Bạn không có quyền xem toàn bộ danh sách bệnh nhân của hệ thống.");
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
    @Transactional
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

    /**
     * Permanently removes a patient and every row/file that exists only because
     * of that patient: examinations, DICOM instances, AI analyses/results,
     * diagnosis reviews, reports, and any RAG index entry for those reports.
     *
     * <p>{@code Patient} declares no JPA relationships, so nothing here cascades
     * from deleting it directly - every child table is walked and cleared by
     * hand, in FK-safe order, before the patient row itself is removed. Once the
     * database side commits, collected file paths are deleted on a best-effort
     * basis: a locked or already-missing file only logs a warning and never
     * rolls back the database changes, since the patient must be gone from the
     * system regardless of filesystem state.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePatientCompletelyByCode(String patientCode) {
        Patient patient = patientRepository.findByPatientCode(patientCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient with code " + patientCode + " not found"));

        List<String> filesToDelete = new ArrayList<>();
        List<Examination> examinations = examinationRepository.findByPatientId(patient.getId());

        for (Examination examination : examinations) {
            // chat_sessions.examination_id is only guaranteed ON DELETE SET NULL on
            // environments that ran the manual prod migration; environments relying
            // on Hibernate auto-DDL (local/dev) get a plain restricting FK, so the
            // examination delete below would fail unless this is detached first.
            chatSessionRepository.detachExamination(examination.getId());

            for (DicomInstance instance : dicomInstanceRepository.findByExaminationId(examination.getId())) {
                addIfPresent(filesToDelete, resolveMediaPath(
                        instance.getDicomRaw() == null ? null : instance.getDicomRaw().getFilePath()));
                addIfPresent(filesToDelete, resolveMediaPath(
                        instance.getImage() == null ? null : instance.getImage().getFilePath()));
                addIfPresent(filesToDelete, resolveMediaPath(
                        instance.getAnnotatedImage() == null ? null : instance.getAnnotatedImage().getFilePath()));

                List<Long> orphanImageIds = new ArrayList<>();
                AiAnalysis analysis = instance.getAiAnalysis();
                if (analysis != null && analysis.getAiResults() != null) {
                    for (AiResult result : analysis.getAiResults()) {
                        // DiagnosisReview.aiResult is a NOT NULL, UNIQUE FK that is not
                        // cascaded from AiResult, so it must be gone before the
                        // DicomInstance cascade below deletes the AiResult it points to.
                        diagnosisReviewRepository.findByAiResultId(result.getId())
                                .ifPresent(diagnosisReviewRepository::delete);
                        if (result.getRoiImage() != null) {
                            addIfPresent(filesToDelete, resolveMediaPath(result.getRoiImage().getFilePath()));
                            orphanImageIds.add(result.getRoiImage().getId());
                        }
                        if (result.getGradcamImage() != null) {
                            addIfPresent(filesToDelete, resolveMediaPath(result.getGradcamImage().getFilePath()));
                            orphanImageIds.add(result.getGradcamImage().getId());
                        }
                    }
                }

                // CascadeType.ALL on DicomInstance removes dicomRaw, image, annotatedImage,
                // aiAnalysis, and every aiResult/confidenceScore under it in one call.
                dicomInstanceRepository.delete(instance);
                // roiImage/gradcamImage are a plain @ManyToOne on AiResult (the FK column
                // lives on ai_result), so they are never cascaded and must go separately.
                orphanImageIds.forEach(imageId ->
                        imageRepository.findById(imageId).ifPresent(imageRepository::delete));
            }

            for (Report report : reportRepository.findByExaminationId(examination.getId())) {
                addIfPresent(filesToDelete, resolveReportPath(report.getFilePath()));
                unindexReportKnowledge(report.getId());
                reportRepository.delete(report);
            }

            examinationRepository.delete(examination);
        }

        patientRepository.delete(patient);
        log.info("Permanently deleted patient {} (code {}) with {} examination(s)",
                patient.getId(), patientCode, examinations.size());

        deleteFilesBestEffort(filesToDelete);
    }

    /**
     * Removes the Qdrant vector and knowledge_documents row indexed for an
     * approved report, if any, so a deleted patient's clinical data does not
     * keep surfacing in RAG chat answers afterward. Falls back to a plain
     * repository delete when chat/RAG is disabled (no VectorStore bean exists).
     */
    private void unindexReportKnowledge(Long reportId) {
        knowledgeDocumentRepository.findBySourceKey("report:" + reportId).ifPresent(document -> {
            if (knowledgeIngestionService.isPresent()) {
                knowledgeIngestionService.get().delete(document.getId());
            } else {
                knowledgeDocumentRepository.delete(document);
            }
        });
    }

    private void deleteFilesBestEffort(List<String> absolutePaths) {
        for (String absolutePath : absolutePaths) {
            try {
                Files.deleteIfExists(Path.of(absolutePath));
            } catch (Exception exception) {
                log.warn("Could not delete file {} while purging a patient's data; left on disk",
                        absolutePath, exception);
            }
        }
    }

    private String resolveMediaPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        String relative = storedPath.startsWith("/") ? storedPath.substring(1) : storedPath;
        return Path.of(storageBaseDir, relative).normalize().toString();
    }

    private String resolveReportPath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path exportRoot = Path.of(reportExportDir).toAbsolutePath().normalize();
        Path resolved = exportRoot.resolve(fileName).normalize();
        return resolved.startsWith(exportRoot) ? resolved.toString() : null;
    }

    private void addIfPresent(List<String> paths, String path) {
        if (path != null) {
            paths.add(path);
        }
    }
}
