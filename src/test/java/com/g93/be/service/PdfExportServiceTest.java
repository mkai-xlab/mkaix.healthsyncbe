package com.g93.be.service;

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
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
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

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Path;
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
        mockDoctor.setFullName("Dr. Smith");

        mockExamination = new Examination();
        mockExamination.setId(1L);
        mockExamination.setEncounterCode("ENC-123");
        mockExamination.setPatient(mockPatient);
        mockExamination.setDoctor(mockDoctor);
        mockExamination.setVisitTime(LocalDateTime.now());
        mockExamination.setClinicalNotes("Test notes");
        mockExamination.setFinalDiagnosis("Test diagnosis");
        lenient().when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of());
    }

    @Test
    void generateAndSavePdfReport_ExaminationNotFound_ThrowsException() {
        // Arrange
        when(examinationRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L);
        });

        assertEquals("Examination not found with id: 1", exception.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_Success() {
        // Arrange
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        // Provide a minimal valid HTML for the PDF Renderer
        String dummyHtml = "<html><head></head><body><h1>Test Report</h1></body></html>";
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class))).thenReturn(dummyHtml);

        String filePath = pdfExportService.generateAndSavePdfReport(1L);

        assertNotNull(filePath);
        assertTrue(filePath.contains("report_ENC-123_"));
        assertTrue(filePath.endsWith(".pdf"));

        File createdFile = new File(filePath);
        assertTrue(createdFile.exists());
        assertTrue(createdFile.length() > 0);

        verify(examinationRepository).findById(1L);
        verify(examinationRepository).save(mockExamination);
        assertEquals(ExaminationStatus.REPORT_GENERATED, mockExamination.getStatus());
        verify(templateEngine).process(eq("pdf/report-template"), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_TemplateEngineError_ThrowsException() {
        // Arrange
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(
                List.of(instance(aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2))));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L);
        });

        assertTrue(exception.getMessage().contains("Template processing failed"));
        verify(examinationRepository, never()).save(mockExamination);
    }

    @Test
    void generateAndSavePdfReport_RejectsExaminationWithoutAiResults() {
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L));

        assertEquals("Examination has no AI results to export", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_UsesAiGradeWhenDoctorConfirmedAi() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.AI_CONFIRMED, 2);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L);

        PdfReportDataDto data = capturedReportData();
        assertEquals("2", data.getAiResults().getFirst().getKlGrade());
        assertEquals("2", data.getAiResults().getFirst().getAiPredictedGrade());
        assertEquals("AI_CONFIRMED", data.getAiResults().getFirst().getDecision());
    }

    @Test
    void generateAndSavePdfReport_UsesAdjustedGradeWhenDoctorChangedKl() {
        AiResult aiResult = aiResult(2, DiagnosisReviewDecision.DOCTOR_ADJUSTED, 4);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L);

        PdfReportDataDto data = capturedReportData();
        assertEquals("4", data.getAiResults().getFirst().getKlGrade());
        assertEquals("2", data.getAiResults().getFirst().getAiPredictedGrade());
        assertEquals("DOCTOR_ADJUSTED", data.getAiResults().getFirst().getDecision());
    }

    @Test
    void generateAndSavePdfReport_RejectsUnconfirmedAiResult() {
        AiResult aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(2);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance(aiResult)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pdfExportService.generateAndSavePdfReport(1L));

        assertEquals("AI result with ID 19 has not been confirmed", error.getMessage());
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_UsesOnlyLatestAiAnalysis() {
        AiResult oldUnconfirmedResult = new AiResult();
        oldUnconfirmedResult.setId(18L);
        oldUnconfirmedResult.setPredictedGrade(1);
        AiResult latestConfirmedResult = aiResult(3, DiagnosisReviewDecision.AI_CONFIRMED, 3);
        AiAnalysis oldAnalysis = analysis(17L, LocalDateTime.now().minusDays(1), oldUnconfirmedResult);
        AiAnalysis latestAnalysis = analysis(20L, LocalDateTime.now(), latestConfirmedResult);
        DicomInstance instance = new DicomInstance();
        instance.setAiAnalyses(List.of(oldAnalysis, latestAnalysis));
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(dicomInstanceRepository.findByExaminationId(1L)).thenReturn(List.of(instance));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenReturn("<html><body>Report</body></html>");

        pdfExportService.generateAndSavePdfReport(1L);

        PdfReportDataDto data = capturedReportData();
        assertEquals(1, data.getAiResults().size());
        assertEquals("3", data.getAiResults().getFirst().getKlGrade());
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

    private DicomInstance instance(AiResult aiResult) {
        AiAnalysis analysis = analysis(17L, LocalDateTime.now(), aiResult);
        DicomInstance instance = new DicomInstance();
        instance.setAiAnalyses(List.of(analysis));
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
