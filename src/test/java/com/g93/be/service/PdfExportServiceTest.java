package com.g93.be.service;

import com.g93.be.chat.ReportKnowledgeSyncRequestedEvent;
import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DiagnosisReviewDecision;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Doctor;
import com.g93.be.dto.PdfReportDataDto;
import com.g93.be.dto.ReportResponse;
import com.g93.be.entity.Report;
import com.g93.be.entity.Role;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.ReportRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @TempDir
    Path tempDirectory;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private ExaminationRepository examinationRepository;

    @Mock
    private DicomInstanceRepository dicomInstanceRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PdfExportService pdfExportService;

    private Examination mockExamination;
    private Patient mockPatient;
    private Doctor mockDoctor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pdfExportService, "exportDir", tempDirectory.toString());
        mockPatient = new Patient();
        mockPatient.setPatientCode("PAT-123");
        mockPatient.setFullName("John Doe");
        mockPatient.setDob(LocalDate.of(1990, 1, 1));

        mockDoctor = new Doctor();
        mockDoctor.setId(7L);
        mockDoctor.setUsername("doctor");
        mockDoctor.setFullName("Dr. Smith");

        mockExamination = new Examination();
        mockExamination.setId(1L);
        mockExamination.setEncounterCode("ENC-123");
        mockExamination.setPatient(mockPatient);
        mockExamination.setDoctor(mockDoctor);
        mockExamination.setVisitTime(LocalDateTime.now());
        mockExamination.setClinicalNotes("Test notes");
        mockExamination.setFinalDiagnosis("Test diagnosis");
        mockExamination.setStatus(ExaminationStatus.VERIFIED);
        lenient().when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of());
        lenient().when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(mockDoctor));
        lenient().when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(31L);
            return report;
        });
    }

    @Test
    void generateAndSavePdfReport_ExaminationNotFound_ThrowsException() {
        // Arrange
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L, "doctor");
        });

        assertEquals("Examination not found with id: 1", exception.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_Success() {
        // Arrange
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        // Provide a minimal valid HTML for the PDF Renderer
        String dummyHtml = "<html><head></head><body><h1>Test Report</h1></body></html>";
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class))).thenReturn(dummyHtml);

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor");

        assertEquals(31L, response.reportId());
        assertTrue(response.fileName().startsWith("report_ENC-123_"));
        assertTrue(response.fileName().endsWith(".pdf"));
        assertEquals("/api/v1/reports/1/preview", response.previewUrl());
        assertEquals("/api/v1/reports/1/download", response.downloadUrl());

        File createdFile = tempDirectory.resolve(response.fileName()).toFile();
        assertTrue(createdFile.exists());
        assertTrue(createdFile.length() > 0);

        verify(examinationRepository).findByIdForUpdate(1L);
        verify(examinationRepository).save(mockExamination);
        verify(reportRepository).save(any(Report.class));
        assertEquals(ExaminationStatus.REPORT_GENERATED, mockExamination.getStatus());
        verify(templateEngine).process(eq("pdf/report-template"), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_TemplateEngineError_ThrowsException() {
        // Arrange
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L, "doctor");
        });

        assertTrue(exception.getMessage().contains("Template processing failed"));
        verify(examinationRepository, never()).save(mockExamination);
    }

    @Test
    void generateAndSavePdfReport_RejectsExaminationWithoutAiResults() {
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor"));

        assertEquals("Examination has no AI results to export", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_UsesAiGradeWhenDoctorConfirmedAi() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor");

        PdfReportDataDto data = capturedReportData();
        assertEquals("2", data.getAiResults().getFirst().getKlGrade());
        assertEquals("2", data.getAiResults().getFirst().getAiPredictedGrade());
        assertEquals("AI_CONFIRMED", data.getAiResults().getFirst().getDecision());
        assertEquals("", data.getProcessingTime());
    }

    @Test
    void generateAndSavePdfReport_UsesAdjustedGradeWhenDoctorChangedKl() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 4);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor");

        PdfReportDataDto data = capturedReportData();
        assertEquals("4", data.getAiResults().getFirst().getKlGrade());
        assertEquals("2", data.getAiResults().getFirst().getAiPredictedGrade());
        assertEquals("DOCTOR_ADJUSTED", data.getAiResults().getFirst().getDecision());
    }

    @Test
    void generateAndSavePdfReportMapsAvailableFormFieldsAndLeavesUnavailableFieldsBlank() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 3);
        aiResult.setKneeSide("RIGHT");
        DicomInstance dicom = instance(aiResult);
        dicom.setId(44L);
        dicom.setSopInstanceUid("1.2.840.113619.44");
        dicom.setModality("CR");
        dicom.getAiAnalysis().setDuration(12L);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(dicom));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor");

        PdfReportDataDto data = capturedReportData();
        PdfReportDataDto.AiResultExportDto result = data.getAiResults().getFirst();
        assertEquals("1.2.840.113619.44", result.getDicomInstanceId());
        assertEquals("RIGHT", result.getKneeSide());
        assertEquals("CR", result.getModality());
        assertEquals("DICOM", result.getImageFormat());
        assertEquals("0.01", result.getInferenceTime());
        assertEquals("3", result.getConsensusKlGrade());
        assertEquals("AI_LOWER", result.getComparisonResult());
        assertEquals("3", data.getRightKlGrade());
        assertEquals("", data.getLeftKlGrade());
        assertEquals("0.01", data.getProcessingTime());
        assertEquals("", result.getReaderOneKlGrade());
        assertEquals("", result.getManufacturer());
    }

    @Test
    void generateAndSavePdfReportMapsFinalConfirmedGradesForBothKnees() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2);
        right.setKneeSide("R");
        AiResult left = aiResult(1, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 4);
        left.setKneeSide("Gối trái");

        AiAnalysis analysis = analysis(17L, LocalDateTime.now(), right);
        analysis.setAiResults(List.of(right, left));
        analysis.setDuration(2_500L);
        DicomInstance dicom = new DicomInstance();
        dicom.setId(44L);
        dicom.setAiAnalysis(analysis);

        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(dicom));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor");

        PdfReportDataDto data = capturedReportData();
        assertEquals("2", data.getRightKlGrade());
        assertEquals("4", data.getLeftKlGrade());
        assertEquals("2.50", data.getProcessingTime());
    }

    @Test
    void generateAndSavePdfReport_RejectsUnconfirmedAiResult() {
        AiResult aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(2);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor"));

        assertEquals("AI result with ID 19 has not been confirmed", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_ReturnsExistingReportWithoutGeneratingAgain() throws Exception {
        mockExamination.setStatus(ExaminationStatus.REPORT_GENERATED);
        Files.write(tempDirectory.resolve("existing.pdf"), new byte[]{1, 2, 3});
        Report existing = report("existing.pdf");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(existing));

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor");

        assertEquals(31L, response.reportId());
        assertEquals("existing.pdf", response.fileName());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
        verify(reportRepository, never()).save(any());
        verify(eventPublisher).publishEvent(new ReportKnowledgeSyncRequestedEvent(31L));
    }

    @Test
    void getReportFileByExaminationId_RejectsUnassignedDoctor() {
        Doctor otherDoctor = new Doctor();
        otherDoctor.setId(8L);
        otherDoctor.setUsername("other");
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherDoctor));
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(report("existing.pdf")));

        assertThrows(AccessDeniedException.class,
                () -> pdfExportService.getReportFileByExaminationId(1L, "other"));
    }

    @Test
    void getReportFileByExaminationId_RejectsPathOutsideReportDirectory() {
        Report report = report("existing.pdf");
        report.setFilePath("../secret.pdf");
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(report));

        assertThrows(AccessDeniedException.class,
                () -> pdfExportService.getReportFileByExaminationId(1L, "doctor"));
    }

    @Test
    void getReportFileByExaminationId_ReturnsLatestStoredPdfForAssignedDoctor() throws Exception {
        Files.write(tempDirectory.resolve("existing.pdf"), new byte[]{1, 2, 3});
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(report("existing.pdf")));

        PdfExportService.ReportFile reportFile = pdfExportService
                .getReportFileByExaminationId(1L, "doctor");

        assertEquals("existing.pdf", reportFile.fileName());
        assertEquals("application/pdf", reportFile.contentType());
        assertEquals(3L, reportFile.fileSize());
        assertTrue(reportFile.resource().exists());
    }

    @Test
    void getReportFileByExaminationId_RejectsExaminationWithoutReport() {
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.getReportFileByExaminationId(1L, "doctor"));

        assertEquals("Report not found for examination with id: 1", error.getMessage());
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void generateAndSavePdfReport_DeletesFileWhenReportCannotBeSaved() throws Exception {
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");
        when(reportRepository.save(any(Report.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor"));

        assertTrue(error.getMessage().contains("Database unavailable"));
        try (java.util.stream.Stream<Path> files = Files.list(tempDirectory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void getReportFileByExaminationId_AllowsDepartmentHeadForUnassignedExamination() throws Exception {
        Doctor departmentHead = new Doctor();
        departmentHead.setId(99L);
        departmentHead.setUsername("head");
        Role role = new Role();
        role.setCode("HEAD_OF_DEPARTMENT");
        departmentHead.setRole(role);
        Files.write(tempDirectory.resolve("existing.pdf"), new byte[]{1, 2, 3});
        when(userRepository.findByUsername("head")).thenReturn(Optional.of(departmentHead));
        when(reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(report("existing.pdf")));

        PdfExportService.ReportFile reportFile = pdfExportService
                .getReportFileByExaminationId(1L, "head");

        assertEquals("existing.pdf", reportFile.fileName());
    }



    private AiResult aiResult(
            Integer predictedGrade,
            DiagnosisReviewDecision decision,
            Integer confirmedGrade) {
        AiResult aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(predictedGrade);
        aiResult.setConfidence(0.91);
        aiResult.setDescription("AI interpretation");
        DiagnosisReview review = new DiagnosisReview();
        review.setAiResult(aiResult);
        review.setConfirmedKlGrade(confirmedGrade);
        review.setDecision(decision);
        review.setReviewNote("Reviewed result");
        aiResult.setDiagnosisReview(review);
        return aiResult;
    }

    private Report report(String fileName) {
        Report report = new Report();
        report.setId(31L);
        report.setExamination(mockExamination);
        report.setFilePath(fileName);
        report.setFileName(fileName);
        report.setFileSize(3L);
        report.setContentType("application/pdf");
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }

    private DicomInstance instance(AiResult aiResult) {
        AiAnalysis analysis = analysis(17L, LocalDateTime.now(), aiResult);
        DicomInstance instance = new DicomInstance();
        instance.setAiAnalysis(analysis);
        return instance;
    }

    private AiAnalysis analysis(Long id, LocalDateTime startTime, AiResult aiResult) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setId(id);
        analysis.setStartTime(startTime);
        analysis.setAiResults(List.of(aiResult));
        return analysis;
    }

    private PdfReportDataDto capturedReportData() {
        org.mockito.ArgumentCaptor<IContext> contextCaptor = org.mockito.ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("pdf/report-template"), contextCaptor.capture());
        return (PdfReportDataDto) contextCaptor.getValue().getVariable("data");
    }
}
