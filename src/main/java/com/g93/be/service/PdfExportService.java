package com.g93.be.service;

import com.g93.be.dto.PdfReportDataDto;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Patient;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExportService {

    private final SpringTemplateEngine templateEngine;
    private final ExaminationRepository examinationRepository;

    @Value("${app.pdf.export-dir:D:/HealthSync_Exports}")
    private String exportDir;

    @Transactional(readOnly = true)
    public String generateAndSavePdfReport(Long examinationId) {
        // 1. Fetch data
        Examination examination = examinationRepository.findById(examinationId)
                .orElseThrow(() -> new IllegalArgumentException("Examination not found with id: " + examinationId));
        
        Patient patient = examination.getPatient();
        
        // 2. Prepare AI Results
        List<PdfReportDataDto.AiResultExportDto> aiResultExportDtos = new ArrayList<>();
        
        // Ensure examination has dicomInstances and traverse them
        // Note: You need to make sure the relationship is eager or fetched within a transaction. 
        // For simplicity, assuming it's accessible or we can fetch manually.
        // Assuming there is a way to get dicom instances. If not mapped bidirectionally, you'd need a repository query.
        // The DBML shows examinations ||--o{ xray_images : "examination_id". In code it might be XrayImage or DicomInstance.
        // Let's assume you have a way to fetch AiResults directly, or we can just mock it if it fails lazy loading.
        
        // To be safe against LazyInitializationException in this draft, we'll try catching errors or just leaving it empty if null.
        try {
            // This is a placeholder logic based on entity relationships. You might need to adjust it based on exact Fetch types.
            // if you have a repository like dicomInstanceRepository.findByExaminationId, use it here.
            // For now we will map empty results if we can't traverse.
        } catch (Exception e) {
            log.error("Error fetching AI results", e);
        }

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
            log.info("PDF exported successfully to: {}", outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            log.error("Failed to generate PDF", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
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
