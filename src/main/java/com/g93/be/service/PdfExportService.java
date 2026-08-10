package com.g93.be.service;

import com.g93.be.aspect.LogAction;
import com.g93.be.chat.ReportKnowledgeSyncRequestedEvent;
import com.g93.be.dto.PdfReportDataDto;
import com.g93.be.dto.ReportResponse;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DicomInstance;
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
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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

    @Value("${app.pdf.export-dir}")
    private String exportDir;

    @Transactional
    @LogAction("GENERATE_PDF_REPORT")
    public ReportResponse generateAndSavePdfReport(Long examinationId, String username) {
        Examination examination = examinationRepository.findByIdForUpdate(examinationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Examination not found with id: " + examinationId));
        User currentUser = getUser(username);
        authorizeReportAccess(examination, currentUser);

        if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
            Report existingReport = reportRepository
                    .findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                    .filter(this::reportFileExists)
                    .orElse(null);
            if (existingReport != null) {
                return toResponse(existingReport);
            }
        }
        if (examination.getStatus() != ExaminationStatus.VERIFIED
                && examination.getStatus() != ExaminationStatus.REPORT_GENERATED) {
            throw new IllegalArgumentException(
                    "Examination must be verified before generating its report");
        }

        Patient patient = examination.getPatient();
        FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
        PdfReportDataDto dataDto = buildReportData(examination, patient, finalAiResults);

        Context context = new Context();
        context.setVariable("data", dataDto);
        String htmlContent = templateEngine.process("pdf/report-template", context);

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

    private PdfReportDataDto buildReportData(
            Examination examination,
            Patient patient,
            FinalAiResults finalAiResults) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<PdfReportDataDto.AiResultExportDto> aiResults = finalAiResults.results();
        return PdfReportDataDto.builder()
                .patientCode(patient.getPatientCode())
                .patientName(patient.getFullName())
                .dob(patient.getDob() != null ? patient.getDob().format(dateFormatter) : "")
                .age(formatAge(patient, examination))
                .gender(patient.getGender() != null ? patient.getGender().name() : "")
                .address(valueOrBlank(patient.getAddress()))
                .encounterCode(valueOrBlank(examination.getEncounterCode()))
                .visitTime(examination.getVisitTime() != null
                        ? examination.getVisitTime().format(dateTimeFormatter) : "")
                .doctorName(examination.getDoctor() != null
                        ? valueOrBlank(examination.getDoctor().getFullName()) : "")
                .clinicalNotes(valueOrBlank(examination.getClinicalNotes()))
                .finalDiagnosis(valueOrBlank(examination.getFinalDiagnosis()))
                .leftKlGrade(finalGradeForSide(aiResults, "LEFT"))
                .rightKlGrade(finalGradeForSide(aiResults, "RIGHT"))
                .processingTime(finalAiResults.totalDurationMillis() == null
                        ? "" : formatDuration(finalAiResults.totalDurationMillis()))
                .aiResults(aiResults)
                .build();
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

    private FinalAiResults buildFinalAiResults(Long examinationId) {
        List<PdfReportDataDto.AiResultExportDto> results = new ArrayList<>();
        Set<Long> countedAnalysisIds = new HashSet<>();
        long totalDurationMillis = 0L;
        boolean hasDuration = false;
        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examinationId);
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Examination has no AI results to export");
        }
        for (DicomInstance instance : instances) {
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
                results.add(PdfReportDataDto.AiResultExportDto.builder()
                        .dicomInstanceId(dicomIdentifier(instance))
                        .kneeSide(resolveKneeSide(aiResult, instance))
                        .klGrade(String.valueOf(review.getConfirmedKlGrade()))
                        .aiPredictedGrade(String.valueOf(aiResult.getPredictedGrade()))
                        .decision(review.getDecision().name())
                        .confidence(formatConfidence(aiResult.getConfidence()))
                        .inferenceTime(formatDuration(latestAnalysis.getDuration()))
                        .modality(valueOrBlank(instance.getModality()))
                        .imageFormat("DICOM")
                        .manufacturer("")
                        .acquisitionPosition("")
                        .imageQuality("")
                        .readerOneOsteophyte("")
                        .readerTwoOsteophyte("")
                        .readerOneJointSpace("")
                        .readerTwoJointSpace("")
                        .readerOneSubchondralSclerosis("")
                        .readerTwoSubchondralSclerosis("")
                        .readerOneBoneDeformity("")
                        .readerTwoBoneDeformity("")
                        .readerOneKlGrade("")
                        .readerTwoKlGrade("")
                        .consensusKlGrade(String.valueOf(review.getConfirmedKlGrade()))
                        .readerOneProcessingTime("")
                        .readerTwoProcessingTime("")
                        .osteophyteDetection("")
                        .jointSpaceDetection("")
                        .comparisonResult(formatComparison(
                                aiResult.getPredictedGrade(), review.getConfirmedKlGrade()))
                        .errorAnalysisNote(valueOrBlank(review.getReviewNote()))
                        .interpretation(aiResult.getDescription())
                        .reviewNote(review.getReviewNote())
                        .gradcamBase64(fetchImageAsBase64(
                                aiResult.getStorageHeatmapFilePath() != null
                                        ? aiResult.getStorageHeatmapFilePath()
                                        : aiResult.getGradcamImage() != null
                                                ? aiResult.getGradcamImage().getFilePath()
                                                : null))
                        .build());
            }
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

    private String finalGradeForSide(
            List<PdfReportDataDto.AiResultExportDto> aiResults,
            String expectedSide) {
        return aiResults.stream()
                .filter(result -> result != null && expectedSide.equals(normalizeKneeSide(result.getKneeSide())))
                .map(result -> result.getKlGrade())
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
        if (patient.getDob() == null) {
            return "";
        }
        java.time.LocalDate reference = examination.getVisitTime() != null
                ? examination.getVisitTime().toLocalDate()
                : java.time.LocalDate.now();
        return String.valueOf(Period.between(patient.getDob(), reference).getYears());
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

    private record FinalAiResults(
            List<PdfReportDataDto.AiResultExportDto> results,
            Long totalDurationMillis) {
    }
}
