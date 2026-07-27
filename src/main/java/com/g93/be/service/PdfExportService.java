package com.g93.be.service;

import com.g93.be.dto.PdfReportDataDto;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.entity.Patient;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExportService {

    private final SpringTemplateEngine templateEngine;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;

    @Value("${app.pdf.export-dir:D:/HealthSync_Exports}")
    private String exportDir;

    @Transactional
    public String generateAndSavePdfReport(Long examinationId) {
        // 1. Fetch data
        Examination examination = examinationRepository.findById(examinationId)
                .orElseThrow(() -> new IllegalArgumentException("Examination not found with id: " + examinationId));
        
        Patient patient = examination.getPatient();
        
        // 2. Export only the final grade explicitly confirmed by a reviewer.
        List<PdfReportDataDto.AiResultExportDto> aiResultExportDtos = buildFinalAiResults(examinationId);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // 3. Map to DTO
        PdfReportDataDto dataDto = PdfReportDataDto.builder()
                .patientCode(patient.getPatientCode())
                .patientName(patient.getFullName())
                .dob(patient.getDob() != null ? patient.getDob().format(dobFormatter) : "N/A")
                .gender(patient.getGender() != null ? patient.getGender().name() : "N/A")
                .address(patient.getAddress() != null ? patient.getAddress() : "N/A")
                .encounterCode(examination.getEncounterCode())
                .visitTime(examination.getVisitTime() != null ? examination.getVisitTime().format(dtf) : "N/A")
                .doctorName(examination.getDoctor() != null ? examination.getDoctor().getFullName() : "N/A")
                .clinicalNotes(examination.getClinicalNotes())
                .finalDiagnosis(examination.getFinalDiagnosis())
                .aiResults(aiResultExportDtos) // currently empty, needs actual DB mapping
                .build();

        // 4. Render HTML
        Context context = new Context();
        context.setVariable("data", dataDto);
        String htmlContent = templateEngine.process("pdf/report-template", context);

        // 5. Generate PDF
        String fileName = "report_" + examination.getEncounterCode() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        File exportDirectory = new File(exportDir);
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs();
        }
        
        File outputFile = new File(exportDirectory, fileName);

        try (FileOutputStream os = new FileOutputStream(outputFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, "/");
            
            // Add font
            ClassPathResource fontResource = new ClassPathResource("fonts/tahoma.ttf");
            if (fontResource.exists()) {
                builder.useFont(fontResource.getFile(), "Tahoma");
            } else {
                log.warn("Tahoma font not found in resources!");
            }
            
            builder.toStream(os);
            builder.run();
            examination.setStatus(ExaminationStatus.REPORT_GENERATED);
            examinationRepository.save(examination);
            log.info("PDF exported successfully to: {}", outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            log.error("Failed to generate PDF", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    private List<PdfReportDataDto.AiResultExportDto> buildFinalAiResults(Long examinationId) {
        List<PdfReportDataDto.AiResultExportDto> results = new ArrayList<>();
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
            for (AiResult aiResult : latestAnalysis.getAiResults()) {
                DiagnosisReview review = aiResult.getDiagnosisReview();
                if (review == null) {
                    throw new IllegalArgumentException(
                            "AI result with ID " + aiResult.getId() + " has not been confirmed");
                }
                results.add(PdfReportDataDto.AiResultExportDto.builder()
                        .klGrade(String.valueOf(review.getConfirmedKlGrade()))
                        .aiPredictedGrade(String.valueOf(aiResult.getPredictedGrade()))
                        .decision(review.getDecision().name())
                        .confidence(formatConfidence(aiResult.getConfidence()))
                        .interpretation(aiResult.getDescription())
                        .reviewNote(review.getReviewNote())
                        .build());
            }
        }
        return results;
    }

    private String formatConfidence(Double confidence) {
        return confidence == null ? "N/A" : String.format(Locale.US, "%.2f", confidence * 100);
    }

    private String fetchImageAsBase64(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        try (InputStream in = new URL(imageUrl).openStream()) {
            byte[] bytes = in.readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("Could not fetch image for PDF: {}", imageUrl, e);
            return null;
        }
    }
}
