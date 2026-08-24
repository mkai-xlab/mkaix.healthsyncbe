package com.g93.be.service;

import com.g93.be.aspect.LogAction;
import com.g93.be.chat.ReportKnowledgeSyncRequestedEvent;
import com.g93.be.config.XrayReportProperties;
import com.g93.be.dto.GenerateReportRequest;
import com.g93.be.dto.ReportDraftResponse;
import com.g93.be.dto.XrayReportDataDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.ReportListItemResponse;
import com.g93.be.dto.ReportResponse;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.DicomInstanceStatus;
import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Report;
import com.g93.be.entity.User;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.ReportRepository;
import com.g93.be.repository.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.text.Normalizer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final SpringTemplateEngine templateEngine;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final XrayReportContentComposer contentComposer;
    private final XrayReportProperties reportProperties;

    /** Letterhead logos are packaged and never change at runtime, so encode them once. */
    private final Map<String, String> logoDataUriCache = new ConcurrentHashMap<>();

    @Value("${app.pdf.export-dir}")
    private String exportDir;

    /**
     * Renders the X-ray report PDF for a verified examination.
     *
     * <p>This is the confirm step of the preview flow: {@code request} carries the form fields the
     * doctor reviewed and edited, and a fresh PDF is always rendered so those values reach the file.
     * Any field left blank falls back to the pre-filled value, and calling this with no body at all
     * returns the previously generated report untouched.
     */
    @Transactional
    @LogAction("GENERATE_PDF_REPORT")
    public ReportResponse generateAndSavePdfReport(
            Long examinationId,
            String username,
            GenerateReportRequest request) {
        Examination examination = examinationRepository.findByIdForUpdate(examinationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Examination not found with id: " + examinationId));
        User currentUser = getUser(username);
        authorizeReportAccess(examination, currentUser);

        if (request == null && examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
            Report existingReport = reportRepository
                    .findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                    .filter(this::reportFileExists)
                    .orElse(null);
            if (existingReport != null) {
                eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(existingReport.getId()));
                return toResponse(existingReport);
            }
        }
        requireVerified(examination, "generating");

        FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
        ReportForm form = applyDoctorEdits(
                buildFormDefaults(examination, currentUser, finalAiResults), request);
        XrayReportDataDto dataDto = buildXrayReportData(form);

        Context context = new Context();
        context.setVariable("data", dataDto);
        String htmlContent = templateEngine.process("pdf/xray-report-template", context);

        Path exportRoot = getExportRoot();
        String fileName = buildFileName(examination);
        Path outputPath = exportRoot.resolve(fileName).normalize();
        Path temporaryPath = null;

        try {
            Files.createDirectories(exportRoot);
            temporaryPath = Files.createTempFile(exportRoot, ".report-", ".tmp");
            renderPdf(htmlContent, temporaryPath);
            moveAtomically(temporaryPath, outputPath);
            temporaryPath = null;
            deleteFileIfTransactionRollsBack(outputPath);

            Report report = new Report();
            report.setExamination(examination);
            report.setOperatingDoctor(currentUser);
            report.setClinicalSummary(examination.getFinalDiagnosis());
            report.setFilePath(fileName);
            report.setFileName(fileName);
            report.setContentType(PDF_CONTENT_TYPE);
            report.setFileSize(Files.size(outputPath));
            report.setCreatedAt(LocalDateTime.now());
            Report savedReport = reportRepository.save(report);
            eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(savedReport.getId()));

            // The result block belongs to the examination, not to any single PDF render, so the
            // next draft can offer the doctor's own wording back instead of the auto-composed text.
            examination.setFindings(String.join("\n", form.findings()));
            examination.setConclusion(form.conclusion());
            examination.setStatus(ExaminationStatus.REPORT_GENERATED);
            examinationRepository.save(examination);
            log.info("PDF report {} generated for examination {}", savedReport.getId(), examinationId);
            return toResponse(savedReport);
        } catch (Exception exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(outputPath);
            log.error("Failed to generate PDF for examination {}", examinationId, exception);
            throw new RuntimeException("Failed to generate PDF: " + exception.getMessage(), exception);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportListItemResponse> getGeneratedReports(Pageable pageable, String username) {
        User currentUser = getUser(username);
        String roleCode = currentUser.getRole() == null ? null : currentUser.getRole().getCode();
        Page<Report> reportPage;

        if ("DOCTOR".equalsIgnoreCase(roleCode)) {
            reportPage = reportRepository.findByExamination_Doctor_Id(currentUser.getId(), pageable);
        } else if (isDepartmentHead(currentUser)) {
            reportPage = reportRepository.findAll(pageable);
        } else {
            throw new AccessDeniedException("Only doctors and department heads can view generated reports");
        }

        return PageResponse.of(reportPage.map(this::toListItemResponse));
    }

    @Transactional(readOnly = true)
    public ReportFile getReportFileByExaminationId(Long examinationId, String username) {
        Report report = reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Report not found for examination with id: " + examinationId));
        authorizeReportAccess(report.getExamination(), getUser(username));

        Path reportPath = resolveReportPath(report);
        if (!Files.isRegularFile(reportPath)) {
            throw new IllegalStateException(
                    "PDF file is missing for examination with id: " + examinationId);
        }
        Resource resource = new FileSystemResource(reportPath);
        String fileName = report.getFileName() == null || report.getFileName().isBlank()
                ? "report-" + examinationId + ".pdf"
                : report.getFileName();
        return new ReportFile(
                resource,
                fileName,
                report.getContentType() == null ? PDF_CONTENT_TYPE : report.getContentType(),
                report.getFileSize() == null ? fileSize(reportPath) : report.getFileSize());
    }

    /**
     * Returns the report form pre-filled for review. The doctor edits it on screen and posts the
     * confirmed values back to the generate endpoint, which is the only step that renders a PDF.
     */
    @Transactional(readOnly = true)
    public ReportDraftResponse getReportDraft(Long examinationId, String username) {
        Examination examination = examinationRepository.findById(examinationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Examination not found with id: " + examinationId));
        User currentUser = getUser(username);
        authorizeReportAccess(examination, currentUser);
        requireVerified(examination, "drafting");

        FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
        ReportForm form = buildFormDefaults(examination, currentUser, finalAiResults);
        Patient patient = examination.getPatient();
        return new ReportDraftResponse(
                examinationId,
                patient == null ? null : patient.getPatientCode(),
                reportProperties.ministryName(),
                reportProperties.hospitalName(),
                reportProperties.departmentName(),
                reportProperties.formCode(),
                reportProperties.clinicalDepartment(),
                form.doctorName(),
                finalGradeForSide(finalAiResults.results(), "LEFT"),
                finalGradeForSide(finalAiResults.results(), "RIGHT"),
                form.documentNumber(),
                form.attemptNumber(),
                form.patientName(),
                form.age(),
                form.gender(),
                form.address(),
                form.findings(),
                form.conclusion(),
                form.signaturePlace(),
                form.signatureDate());
    }

    private void requireVerified(Examination examination, String action) {
        if (examination.getStatus() != ExaminationStatus.VERIFIED
                && examination.getStatus() != ExaminationStatus.REPORT_GENERATED) {
            throw new IllegalArgumentException(
                    "Examination must be verified before " + action + " its report");
        }
    }

    /**
     * Fills every form field from the examination record, the configured letterhead, and the
     * Kellgren-Lawrence grades confirmed during verification. This is what the doctor sees in the
     * preview, and what any field they leave alone falls back to on confirm.
     *
     * <p>The result block prefers whatever the doctor confirmed the last time a report was
     * generated for this examination, since that already reflects their own wording; only a first
     * confirmation falls back to the grade-only text auto-composed from the verified KL grades.
     */
    private ReportForm buildFormDefaults(
            Examination examination,
            User currentUser,
            FinalAiResults finalAiResults) {
        Patient patient = examination.getPatient();
        String leftKlGrade = finalGradeForSide(finalAiResults.results(), "LEFT");
        String rightKlGrade = finalGradeForSide(finalAiResults.results(), "RIGHT");
        List<String> savedFindings = splitLines(examination.getFindings());
        String savedConclusion = valueOrBlank(examination.getConclusion());
        return new ReportForm(
                valueOrBlank(examination.getEncounterCode()),
                // The visit sequence has no source record; the doctor types it in the preview.
                "",
                patient == null ? "" : valueOrBlank(patient.getFullName()),
                formatAge(patient, examination),
                formatGender(patient),
                patient == null ? "" : valueOrBlank(patient.getAddress()),
                savedFindings.isEmpty()
                        ? contentComposer.composeFindings(leftKlGrade, rightKlGrade) : savedFindings,
                savedConclusion.isBlank()
                        ? contentComposer.composeConclusion(leftKlGrade, rightKlGrade) : savedConclusion,
                reportProperties.signaturePlace(),
                LocalDate.now(),
                currentUser == null ? "" : valueOrBlank(currentUser.getFullName()));
    }

    /**
     * Overlays the values the doctor confirmed onto the pre-filled form. Each field falls back
     * independently, so correcting one line never blanks the rest of the sheet.
     */
    private ReportForm applyDoctorEdits(ReportForm defaults, GenerateReportRequest request) {
        if (request == null) {
            return defaults;
        }
        List<String> findings = cleanFindings(request.findings());
        return new ReportForm(
                override(defaults.documentNumber(), request.documentNumber()),
                override(defaults.attemptNumber(), request.attemptNumber()),
                override(defaults.patientName(), request.patientName()),
                override(defaults.age(), request.age()),
                override(defaults.gender(), request.gender()),
                override(defaults.address(), request.address()),
                findings.isEmpty() ? defaults.findings() : findings,
                override(defaults.conclusion(), request.conclusion()),
                override(defaults.signaturePlace(), request.signaturePlace()),
                request.signatureDate() == null ? defaults.signatureDate() : request.signatureDate(),
                // The signature always names the authenticated doctor, never a submitted value.
                defaults.doctorName());
    }

    private String override(String defaultValue, String submitted) {
        return submitted == null || submitted.isBlank() ? defaultValue : submitted.trim();
    }

    private List<String> cleanFindings(List<String> findings) {
        if (findings == null) {
            return List.of();
        }
        return findings.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Stream.of(value.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private XrayReportDataDto buildXrayReportData(ReportForm form) {
        return XrayReportDataDto.builder()
                .ministryName(reportProperties.ministryName())
                .hospitalName(reportProperties.hospitalName())
                .departmentName(reportProperties.departmentName())
                .formCode(reportProperties.formCode())
                .hospitalLogo(logoDataUri("report/logo-hospital.png"))
                .documentNumber(form.documentNumber())
                .attemptNumber(form.attemptNumber())
                .patientName(form.patientName())
                .age(form.age())
                .gender(form.gender())
                .address(form.address())
                // The imaging department is fixed by configuration, never by the doctor.
                .clinicalDepartment(reportProperties.clinicalDepartment())
                .findings(form.findings())
                .conclusionLines(splitLines(form.conclusion()))
                .signaturePlace(form.signaturePlace())
                .signatureDay(formatDayOrMonth(form.signatureDate().getDayOfMonth()))
                .signatureMonth(formatDayOrMonth(form.signatureDate().getMonthValue()))
                .signatureYear(String.valueOf(form.signatureDate().getYear()))
                .doctorName(form.doctorName())
                .build();
    }

    /**
     * Reads a packaged letterhead logo as a data URI. The renderer resolves no external hosts, so
     * the bytes must travel inside the HTML. A missing file degrades to a logo-less letterhead
     * rather than failing the whole report.
     */
    private String logoDataUri(String classpathLocation) {
        String cached = logoDataUriCache.get(classpathLocation);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String dataUri = "";
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (resource.exists()) {
            try (InputStream inputStream = resource.getInputStream()) {
                dataUri = "data:image/png;base64,"
                        + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
            } catch (Exception exception) {
                log.warn("Could not read report logo {}", classpathLocation, exception);
            }
        } else {
            log.warn("Report logo {} is not packaged; rendering the letterhead without it",
                    classpathLocation);
        }
        logoDataUriCache.put(classpathLocation, dataUri);
        return dataUri.isEmpty() ? null : dataUri;
    }

    private String formatDayOrMonth(int value) {
        return String.format(Locale.US, "%02d", value);
    }

    private String formatGender(Patient patient) {
        if (patient == null || patient.getGender() == null) {
            return "";
        }
        return switch (patient.getGender()) {
            case MALE -> "Nam";
            case FEMALE -> "N\u1EEF";
            case OTHER -> "Kh\u00E1c";
        };
    }

    private void renderPdf(String htmlContent, Path outputPath) throws Exception {
        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, "/");

            ClassPathResource fontResource = new ClassPathResource("fonts/tahoma.ttf");
            if (fontResource.exists()) {
                builder.useFont(() -> {
                    try {
                        return fontResource.getInputStream();
                    } catch (java.io.IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }, "Tahoma");
            } else {
                log.warn("Tahoma font not found in resources");
            }
            builder.toStream(outputStream);
            builder.run();
        }
    }

    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String buildFileName(Examination examination) {
        String encounterCode = valueOrBlank(examination.getEncounterCode())
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (encounterCode.isBlank()) {
            encounterCode = String.valueOf(examination.getId());
        }
        return "report_" + encounterCode + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    private void authorizeReportAccess(Examination examination, User currentUser) {
        if (isDepartmentHead(currentUser)) {
            return;
        }
        User assignedDoctor = examination.getDoctor();
        boolean sameUser = assignedDoctor != null
                && (Objects.equals(assignedDoctor.getId(), currentUser.getId())
                || Objects.equals(assignedDoctor.getUsername(), currentUser.getUsername()));
        if (!sameUser) {
            throw new AccessDeniedException("Doctor is not assigned to this examination");
        }
    }

    private boolean isDepartmentHead(User user) {
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return false;
        }
        String roleCode = user.getRole().getCode();
        return "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)
                || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode);
    }

    private boolean reportFileExists(Report report) {
        try {
            return Files.isRegularFile(resolveReportPath(report));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Path resolveReportPath(Report report) {
        if (report.getFilePath() == null || report.getFilePath().isBlank()) {
            throw new IllegalStateException("Report file path is missing");
        }
        Path exportRoot = getExportRoot();
        Path reportPath = exportRoot.resolve(report.getFilePath()).normalize();
        if (!reportPath.startsWith(exportRoot)) {
            throw new AccessDeniedException("Invalid report file path");
        }
        return reportPath;
    }

    private Path getExportRoot() {
        return Paths.get(exportDir).toAbsolutePath().normalize();
    }

    private ReportResponse toResponse(Report report) {
        Long examinationId = report.getExamination().getId();
        String previewUrl = "/api/v1/reports/" + examinationId + "/preview";
        String downloadUrl = "/api/v1/reports/" + examinationId + "/download";
        return new ReportResponse(
                report.getId(),
                report.getExamination().getId(),
                report.getFileName(),
                report.getFileSize(),
                report.getContentType(),
                report.getCreatedAt(),
                previewUrl,
                downloadUrl);
    }

    private ReportListItemResponse toListItemResponse(Report report) {
        Examination examination = report.getExamination();
        Patient patient = examination.getPatient();
        User assignedDoctor = examination.getDoctor();
        Long examinationId = examination.getId();
        String previewUrl = "/api/v1/reports/" + examinationId + "/preview";
        String downloadUrl = "/api/v1/reports/" + examinationId + "/download";
        return new ReportListItemResponse(
                report.getId(),
                examinationId,
                examination.getEncounterCode(),
                examination.getVisitTime(),
                patient == null ? null : patient.getPatientCode(),
                patient == null ? null : patient.getFullName(),
                assignedDoctor == null ? null : assignedDoctor.getId(),
                assignedDoctor == null ? null : assignedDoctor.getFullName(),
                report.getFileName(),
                report.getFileSize(),
                report.getContentType(),
                report.getCreatedAt(),
                previewUrl,
                downloadUrl);
    }

    /**
     * Collects the Kellgren-Lawrence grade each knee was verified with. Only the side and the
     * confirmed grade reach the form, so an unreviewed result is a hard error rather than a blank
     * line on a signed report.
     */
    private FinalAiResults buildFinalAiResults(Long examinationId) {
        List<VerifiedGrade> results = new ArrayList<>();
        Set<Long> countedAnalysisIds = new HashSet<>();
        long totalDurationMillis = 0L;
        boolean hasDuration = false;
        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examinationId);
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Examination has no AI results to export");
        }
        for (DicomInstance instance : instances) {
            if (instance.getStatus() == DicomInstanceStatus.AI_FAILED) {
                continue;
            }
            AiAnalysis latestAnalysis = instance.getAiAnalysis();
            if (latestAnalysis == null
                    || latestAnalysis.getAiResults() == null
                    || latestAnalysis.getAiResults().isEmpty()) {
                throw new IllegalArgumentException(
                        "DICOM instance with ID " + instance.getId() + " has no AI results to export");
            }
            if (latestAnalysis.getDuration() != null
                    && (latestAnalysis.getId() == null || countedAnalysisIds.add(latestAnalysis.getId()))) {
                totalDurationMillis += latestAnalysis.getDuration();
                hasDuration = true;
            }
            for (AiResult aiResult : latestAnalysis.getAiResults()) {
                DiagnosisReview review = aiResult.getDiagnosisReview();
                if (review == null) {
                    throw new IllegalArgumentException(
                            "AI result with ID " + aiResult.getId() + " has not been confirmed");
                }
                results.add(new VerifiedGrade(
                        resolveKneeSide(aiResult, instance),
                        String.valueOf(review.getConfirmedKlGrade())));
            }
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Examination has no successful AI results to export");
        }
        return new FinalAiResults(results, hasDuration ? totalDurationMillis : null);
    }

    private String dicomIdentifier(DicomInstance instance) {
        if (instance.getSopInstanceUid() != null && !instance.getSopInstanceUid().isBlank()) {
            return instance.getSopInstanceUid();
        }
        return instance.getId() == null ? "" : String.valueOf(instance.getId());
    }

    private String resolveKneeSide(AiResult aiResult, DicomInstance instance) {
        String side = valueOrBlank(aiResult.getKneeSide());
        if (side.isBlank()) {
            side = valueOrBlank(instance.getImageLaterality());
        }
        return normalizeKneeSide(side);
    }

    private String finalGradeForSide(List<VerifiedGrade> aiResults, String expectedSide) {
        return aiResults.stream()
                .filter(result -> result != null && expectedSide.equals(normalizeKneeSide(result.kneeSide())))
                .map(VerifiedGrade::klGrade)
                .filter(grade -> grade != null && !grade.isBlank())
                .map(Integer::valueOf)
                .max(Integer::compareTo)
                .map(String::valueOf)
                .orElse("");
    }

    private String normalizeKneeSide(String side) {
        if (side == null) {
            return "";
        }
        String normalized = Normalizer.normalize(side, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (normalized.equals("L") || normalized.equals("LEFT")
                || normalized.equals("TRAI") || normalized.equals("GOI TRAI")) {
            return "LEFT";
        }
        if (normalized.equals("R") || normalized.equals("RIGHT")
                || normalized.equals("PHAI") || normalized.equals("GOI PHAI")) {
            return "RIGHT";
        }
        return normalized;
    }

    private String formatConfidence(Double confidence) {
        return confidence == null ? "" : String.format(Locale.US, "%.2f", confidence * 100);
    }

    private String formatAge(Patient patient, Examination examination) {
        if (patient == null || patient.getDob() == null) {
            return "";
        }
        java.time.LocalDate reference = examination.getVisitTime() != null
                ? examination.getVisitTime().toLocalDate()
                : java.time.LocalDate.now();
        return String.valueOf(Period.between(patient.getDob(), reference).getYears());
    }

    private String formatStudyDateTime(Examination examination) {
        if (examination.getStudyDate() == null) {
            return "";
        }
        if (examination.getStudyTime() == null) {
            return examination.getStudyDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        return LocalDateTime.of(examination.getStudyDate(), examination.getStudyTime())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private String formatDuration(Long duration) {
        return duration == null ? "" : String.format(Locale.US, "%.2f", duration / 1000.0);
    }

    private String formatComparison(Integer predicted, Integer confirmed) {
        if (predicted == null || confirmed == null) {
            return "";
        }
        if (predicted.equals(confirmed)) {
            return "MATCH";
        }
        return predicted > confirmed ? "AI_HIGHER" : "AI_LOWER";
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    private String fetchImageAsBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            byte[] bytes;
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                try (InputStream inputStream = new URL(imageUrl).openStream()) {
                    bytes = inputStream.readAllBytes();
                }
            } else {
                bytes = Files.readAllBytes(Paths.get(imageUrl));
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception exception) {
            log.error("Could not fetch image for PDF: {}", imageUrl, exception);
            return null;
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read PDF file size", exception);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception exception) {
            log.warn("Could not delete incomplete PDF file: {}", path, exception);
        }
    }

    private void deleteFileIfTransactionRollsBack(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(path);
                }
            }
        });
    }

    public record ReportFile(
            Resource resource,
            String fileName,
            String contentType,
            Long fileSize) {
    }

    /** Every field of the report form, pre-filled or confirmed by the doctor. */
    private record ReportForm(
            String documentNumber,
            String attemptNumber,
            String patientName,
            String age,
            String gender,
            String address,
            List<String> findings,
            String conclusion,
            String signaturePlace,
            LocalDate signatureDate,
            String doctorName) {
    }

    /** One knee's verified Kellgren-Lawrence grade, as confirmed by the reviewing doctor. */
    private record VerifiedGrade(String kneeSide, String klGrade) {
    }

    private record FinalAiResults(List<VerifiedGrade> results, Long totalDurationMillis) {
    }
}
