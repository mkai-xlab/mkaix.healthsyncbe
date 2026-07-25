package com.g93.be.service;

import com.g93.be.entity.Examination;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Doctor;
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
        verify(templateEngine).process(eq("pdf/report-template"), any(IContext.class));
    }

    @Test
    void generateAndSavePdfReport_TemplateEngineError_ThrowsException() {
        // Arrange
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExamination));
        when(templateEngine.process(eq("pdf/report-template"), any(IContext.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            pdfExportService.generateAndSavePdfReport(1L);
        });

        assertTrue(exception.getMessage().contains("Template processing failed"));
    }
}
