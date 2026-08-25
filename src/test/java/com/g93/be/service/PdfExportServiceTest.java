package com.g93.be.service;

import com.g93.be.chat.ReportKnowledgeSyncRequestedEvent;
import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DiagnosisReviewDecision;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.DicomInstanceStatus;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Gender;
import com.g93.be.entity.Doctor;
import com.g93.be.config.XrayReportProperties;
import com.g93.be.dto.GenerateReportRequest;
import com.g93.be.dto.ReportDraftResponse;
import com.g93.be.dto.ReportResponse;
import com.g93.be.dto.XrayReportDataDto;
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
import java.time.LocalTime;
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

    private static final XrayReportProperties REPORT_PROPERTIES = new XrayReportProperties(
            "BỘ QUỐC PHÒNG",
            "VIỆN Y HỌC CỔ TRUYỀN QUÂN ĐỘI",
            "KHOA CHẨN ĐOÁN HÌNH ẢNH",
            "08/BV-02",
            "Khoa Chẩn đoán hình ảnh",
            "Hà Nội");

    private PdfExportService pdfExportService;

    private Examination mockExamination;
    private Patient mockPatient;
    private Doctor mockDoctor;

    @BeforeEach
    void setUp() {
        pdfExportService = new PdfExportService(
                templateEngine,
                examinationRepository,
                dicomInstanceRepository,
                reportRepository,
                userRepository,
                eventPublisher,
                new XrayReportContentComposer(),
                REPORT_PROPERTIES);
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
        mockExamination.setStudyDate(LocalDate.of(2026, 8, 20));
        mockExamination.setStudyTime(LocalTime.of(22, 38, 50));
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
            pdfExportService.generateAndSavePdfReport(1L, "doctor", null);
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
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class))).thenReturn(dummyHtml);

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

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
        verify(templateEngine).process(eq("pdf/xray-report-template"), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_SucceedsWhenOtherKneeAiFailed() {
        AiResult survivingResult = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2);
        survivingResult.setKneeSide("RIGHT");
        DicomInstance successfulInstance = instance(survivingResult);
        successfulInstance.setStatus(DicomInstanceStatus.GET_RESULTED);
        DicomInstance failedInstance = new DicomInstance();
        failedInstance.setId(45L);
        failedInstance.setStatus(DicomInstanceStatus.AI_FAILED);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L))
                .thenReturn(List.of(successfulInstance, failedInstance));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Partial AI result</body></html>");

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        assertEquals(31L, response.reportId());
        assertEquals(ExaminationStatus.REPORT_GENERATED, mockExamination.getStatus());
        String findings = capturedFindingsText();
        assertTrue(findings.contains("Gối phải: Thoái hóa khớp gối độ 2 (Kellgren-Lawrence)."));
        assertFalse(findings.contains("Gối trái"));
    }

    @Test
    void generateAndSavePdfReport_RejectsWhenAllKneesAiFailed() {
        DicomInstance failedRight = new DicomInstance();
        failedRight.setId(44L);
        failedRight.setStatus(DicomInstanceStatus.AI_FAILED);
        DicomInstance failedLeft = new DicomInstance();
        failedLeft.setId(45L);
        failedLeft.setStatus(DicomInstanceStatus.AI_FAILED);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L))
                .thenReturn(List.of(failedRight, failedLeft));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor", null));

        assertEquals("Examination has no successful AI results to export", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void generateAndSavePdfReport_TemplateEngineError_ThrowsException() {
        // Arrange
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L, "doctor", null);
        });

        assertTrue(exception.getMessage().contains("Template processing failed"));
        verify(examinationRepository, never()).save(mockExamination);
    }

    @Test
    void generateAndSavePdfReport_RejectsExaminationWithoutAiResults() {
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor", null));

        assertEquals("Examination has no AI results to export", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_UsesAiGradeWhenDoctorConfirmedAi() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2);
        aiResult.setKneeSide("RIGHT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        XrayReportDataDto data = capturedReportData();
        assertTrue(capturedFindingsText().contains("Gối phải: Thoái hóa khớp gối độ 2 (Kellgren-Lawrence)."));
        assertEquals(
                List.of("Hình ảnh thoái hóa khớp gối: gối phải độ 2 theo phân loại Kellgren-Lawrence."),
                data.getConclusionLines());
    }

    @Test
    void generateAndSavePdfReport_UsesAdjustedGradeWhenDoctorChangedKl() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 4);
        aiResult.setKneeSide("LEFT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        XrayReportDataDto data = capturedReportData();
        assertTrue(capturedFindingsText().contains("Gối trái: Thoái hóa khớp gối độ 4 (Kellgren-Lawrence)."));
        assertEquals(
                List.of("Hình ảnh thoái hóa khớp gối: gối trái độ 4 theo phân loại Kellgren-Lawrence."),
                data.getConclusionLines());
    }

    @Test
    void generateAndSavePdfReportMapsAvailableFormFieldsAndLeavesUnavailableFieldsBlank() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 3);
        aiResult.setKneeSide("RIGHT");
        DicomInstance dicom = instance(aiResult);
        dicom.setId(44L);
        dicom.setSopInstanceUid("1.2.840.113619.44");
        mockPatient.setGender(Gender.MALE);
        mockPatient.setAddress("Xã Quang Bị, Thành phố Hà Nội");
        mockExamination.setVisitTime(LocalDateTime.of(2026, 5, 7, 9, 30));
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(dicom));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        XrayReportDataDto data = capturedReportData();
        assertEquals("BỘ QUỐC PHÒNG", data.getMinistryName());
        assertEquals("VIỆN Y HỌC CỔ TRUYỀN QUÂN ĐỘI", data.getHospitalName());
        assertEquals("08/BV-02", data.getFormCode());
        assertEquals("ENC-123", data.getDocumentNumber());
        assertEquals("John Doe", data.getPatientName());
        assertEquals("36", data.getAge());
        assertEquals("Nam", data.getGender());
        assertEquals("Xã Quang Bị, Thành phố Hà Nội", data.getAddress());
        assertEquals("Hà Nội", data.getSignaturePlace());
        assertEquals("Dr. Smith", data.getDoctorName());
        // The imaging department is fixed by configuration rather than taken from the record.
        assertEquals("Khoa Chẩn đoán hình ảnh", data.getClinicalDepartment());
        // The visit sequence has no source record and is written in by hand.
        assertEquals("", data.getAttemptNumber());
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
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        XrayReportDataDto data = capturedReportData();
        String findings = capturedFindingsText();
        assertTrue(findings.contains("Gối phải: Thoái hóa khớp gối độ 2 (Kellgren-Lawrence)."));
        assertTrue(findings.contains("Gối trái: Thoái hóa khớp gối độ 4 (Kellgren-Lawrence)."));
        assertEquals(
                List.of("Hình ảnh thoái hóa khớp gối: gối phải độ 2, gối trái độ 4"
                        + " theo phân loại Kellgren-Lawrence."),
                data.getConclusionLines());
    }

    @Test
    void generateAndSavePdfReport_RejectsUnconfirmedAiResult() {
        AiResult aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(2);
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor", null));

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

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

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
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");
        when(reportRepository.save(any(Report.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L, "doctor", null));

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



    @Test
    void getReportDraft_PreFillsTheWholeFormWithTheResultTextAutoComposedFromVerifiedGrades() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        mockPatient.setGender(Gender.MALE);
        mockPatient.setAddress("Xã Quang Bị, Thành phố Hà Nội");
        mockExamination.setVisitTime(LocalDateTime.of(2026, 5, 7, 9, 30));
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));

        ReportDraftResponse draft = pdfExportService.getReportDraft(1L, "doctor");

        assertEquals("3", draft.rightKlGrade());
        assertEquals("", draft.leftKlGrade());
        assertEquals("PAT-123", draft.patientCode());
        assertEquals("Gối phải: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence).",
                draft.findings().getFirst());
        assertEquals(
                "Hình ảnh thoái hóa khớp gối: gối phải độ 3 theo phân loại Kellgren-Lawrence.",
                draft.conclusion());

        // The rest of the sheet arrives pre-filled so the doctor edits rather than retypes it.
        assertEquals("BỘ QUỐC PHÒNG", draft.ministryName());
        assertEquals("08/BV-02", draft.formCode());
        assertEquals("ENC-123", draft.documentNumber());
        assertEquals("John Doe", draft.patientName());
        assertEquals("36", draft.age());
        assertEquals("Nam", draft.gender());
        assertEquals("Xã Quang Bị, Thành phố Hà Nội", draft.address());
        assertEquals("Khoa Chẩn đoán hình ảnh", draft.clinicalDepartment());
        assertEquals("Hà Nội", draft.signaturePlace());
        assertEquals(LocalDate.now(), draft.signatureDate());
        // The signature line names the authenticated doctor, not anyone on the record.
        assertEquals("Dr. Smith", draft.doctorName());
        // The visit sequence has no source record and is left blank for the doctor to type in.
        assertEquals("", draft.attemptNumber());
    }

    @Test
    void getReportDraft_ReturnsTheDoctorsOwnWordingSavedFromAnEarlierConfirmation() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        mockExamination.setFindings("Hẹp khe khớp đùi chày trong.\nGai xương bờ mâm chày.");
        mockExamination.setConclusion("Thoái hóa khớp gối phải giai đoạn muộn.");
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));

        ReportDraftResponse draft = pdfExportService.getReportDraft(1L, "doctor");

        // The examination already carries the doctor's own wording from a prior confirmation, so
        // the draft must offer that back instead of resetting to the grade-only auto-composed text.
        assertEquals(
                List.of("Hẹp khe khớp đùi chày trong.", "Gai xương bờ mâm chày."),
                draft.findings());
        assertEquals("Thoái hóa khớp gối phải giai đoạn muộn.", draft.conclusion());
    }

    @Test
    void generateAndSavePdfReport_PrintsEveryFormFieldTheDoctorConfirmed() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");
        GenerateReportRequest request = new GenerateReportRequest(
                "SO-2026-77", "2", "HÀ HUY ĐOÀN", "48", "Nam", "Xã Quang Bị, Hà Nội",
                List.of("Bác sĩ mô tả kết quả."), "Kết luận của bác sĩ.",
                "Hà Nội", LocalDate.of(2026, 5, 8));

        pdfExportService.generateAndSavePdfReport(1L, "doctor", request);

        XrayReportDataDto data = capturedReportData();
        assertEquals("SO-2026-77", data.getDocumentNumber());
        assertEquals("2", data.getAttemptNumber());
        assertEquals("HÀ HUY ĐOÀN", data.getPatientName());
        assertEquals("48", data.getAge());
        assertEquals(List.of("Bác sĩ mô tả kết quả."), data.getFindings());
        assertEquals(List.of("Kết luận của bác sĩ."), data.getConclusionLines());
        assertEquals("08", data.getSignatureDay());
        // Fixed by configuration, so the doctor cannot override it from the preview.
        assertEquals("Khoa Chẩn đoán hình ảnh", data.getClinicalDepartment());
        // The signature is autofilled from the authenticated account, not from the request.
        assertEquals("Dr. Smith", data.getDoctorName());
    }

    @Test
    void generateAndSavePdfReport_SignsWithTheAuthenticatedDoctorEvenForADepartmentHead() {
        Doctor departmentHead = new Doctor();
        departmentHead.setId(99L);
        departmentHead.setUsername("head");
        departmentHead.setFullName("BS. Trưởng khoa");
        Role role = new Role();
        role.setCode("HEAD_OF_DEPARTMENT");
        departmentHead.setRole(role);
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        when(userRepository.findByUsername("head")).thenReturn(Optional.of(departmentHead));
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "head", resultEdits(null, "Kết luận."));

        // The head generated it, so the head signs it - not the examination's assigned doctor.
        assertEquals("BS. Trưởng khoa", capturedReportData().getDoctorName());
    }

    @Test
    void generateAndSavePdfReport_EmbedsThePackagedHospitalCrest() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", null);

        XrayReportDataDto data = capturedReportData();
        assertTrue(data.getHospitalLogo().startsWith("data:image/png;base64,"));
    }


    @Test
    void getReportDraft_RejectsExaminationThatIsNotVerified() {
        mockExamination.setStatus(ExaminationStatus.AI_PROCESSING);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.getReportDraft(1L, "doctor"));

        assertEquals("Examination must be verified before drafting its report", error.getMessage());
    }

    @Test
    void getReportDraft_RejectsExaminationWithAnAlreadyGeneratedReport() {
        mockExamination.setStatus(ExaminationStatus.REPORT_GENERATED);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.getReportDraft(1L, "doctor"));

        assertEquals(
                "Report has already been generated for this examination; "
                        + "view the confirmed result via the report preview or download endpoint",
                error.getMessage());
        // The report is final, so no AI/grade lookup should even happen for a locked examination.
        verify(dicomInstanceRepository, never()).findByExaminationId(anyLong());
    }

    @Test
    void getReportDraft_RejectsUnassignedDoctor() {
        Doctor otherDoctor = new Doctor();
        otherDoctor.setId(8L);
        otherDoctor.setUsername("other");
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherDoctor));
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));

        assertThrows(AccessDeniedException.class, () -> pdfExportService.getReportDraft(1L, "other"));
    }

    @Test
    void generateAndSavePdfReport_PrintsAndStoresDoctorEditedResultText() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");
        GenerateReportRequest request = resultEdits(
                List.of("Hẹp khe khớp đùi chày trong.", "  ", "Gai xương bờ mâm chày."),
                "Thoái hóa khớp gối phải giai đoạn muộn.");

        pdfExportService.generateAndSavePdfReport(1L, "doctor", request);

        XrayReportDataDto data = capturedReportData();
        // The blank line the doctor left behind must not print as an empty bullet.
        assertEquals(
                List.of("Hẹp khe khớp đùi chày trong.", "Gai xương bờ mâm chày."),
                data.getFindings());
        assertEquals(List.of("Thoái hóa khớp gối phải giai đoạn muộn."), data.getConclusionLines());

        org.mockito.ArgumentCaptor<Report> reportCaptor =
                org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        // clinical_summary keeps its original meaning (the examination's final diagnosis); the
        // doctor-confirmed result block belongs to the examination instead, asserted below.
        assertEquals("Test diagnosis", reportCaptor.getValue().getClinicalSummary());
        assertEquals("Hẹp khe khớp đùi chày trong.\nGai xương bờ mâm chày.",
                mockExamination.getFindings());
        assertEquals("Thoái hóa khớp gối phải giai đoạn muộn.", mockExamination.getConclusion());
    }

    @Test
    void generateAndSavePdfReport_KeepsAutoFilledFindingsWhenOnlyConclusionIsEdited() {
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L, "doctor",
                resultEdits(null, "Kết luận do bác sĩ tự nhập."));

        XrayReportDataDto data = capturedReportData();
        assertTrue(capturedFindingsText().contains("Gối phải: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence)."));
        assertEquals(List.of("Kết luận do bác sĩ tự nhập."), data.getConclusionLines());
    }

    @Test
    void generateAndSavePdfReport_RegeneratesExistingReportWhenDoctorEditsResultText() throws Exception {
        mockExamination.setStatus(ExaminationStatus.REPORT_GENERATED);
        AiResult right = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        right.setKneeSide("RIGHT");
        Files.write(tempDirectory.resolve("existing.pdf"), new byte[]{1, 2, 3});
        when(examinationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(right)));
        when(templateEngine.process(eq("pdf/xray-report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        ReportResponse response = pdfExportService.generateAndSavePdfReport(1L, "doctor",
                resultEdits(List.of("Bác sĩ mô tả lại kết quả."), null));

        assertTrue(response.fileName().startsWith("report_ENC-123_"));
        assertNotEquals("existing.pdf", response.fileName());
        verify(templateEngine).process(eq("pdf/xray-report-template"), any(IContext.class));
        verify(reportRepository).save(any(Report.class));
    }


    /** Builds a confirm request that edits only the result block and leaves the rest pre-filled. */
    private GenerateReportRequest resultEdits(List<String> findings, String conclusion) {
        return new GenerateReportRequest(
                null, null, null, null, null, null, findings, conclusion, null, null);
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

    private XrayReportDataDto capturedReportData() {
        org.mockito.ArgumentCaptor<IContext> contextCaptor = org.mockito.ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("pdf/xray-report-template"), contextCaptor.capture());
        return (XrayReportDataDto) contextCaptor.getValue().getVariable("data");
    }

    private String capturedFindingsText() {
        return String.join(" | ", capturedReportData().getFindings());
    }
}
