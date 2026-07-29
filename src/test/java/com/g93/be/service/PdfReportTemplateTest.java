package com.g93.be.service;

import com.g93.be.dto.PdfReportDataDto;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportTemplateTest {

    @Test
    void realReportTemplateRendersToPdfWithPackagedFont() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("data", reportData());
        String html = templateEngine.process("pdf/report-template", context);

        ClassPathResource font = new ClassPathResource("fonts/tahoma.ttf");
        assertTrue(font.exists());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, "/");
        builder.useFont(() -> {
            try {
                return font.getInputStream();
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }, "Tahoma");
        builder.toStream(output);
        builder.run();

        byte[] pdf = output.toByteArray();
        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    private PdfReportDataDto reportData() {
        PdfReportDataDto.AiResultExportDto result = PdfReportDataDto.AiResultExportDto.builder()
                .dicomInstanceId("44")
                .kneeSide("RIGHT")
                .klGrade("3")
                .aiPredictedGrade("2")
                .decision("DOCTOR_ADJUSTED")
                .confidence("91.00")
                .inferenceTime("0.12")
                .modality("CR")
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
                .consensusKlGrade("3")
                .readerOneProcessingTime("")
                .readerTwoProcessingTime("")
                .osteophyteDetection("")
                .jointSpaceDetection("")
                .comparisonResult("AI_LOWER")
                .errorAnalysisNote("Doctor adjusted the final KL grade")
                .interpretation("Test interpretation")
                .reviewNote("Reviewed")
                .build();
        return PdfReportDataDto.builder()
                .patientCode("PAT-001")
                .patientName("Nguyen Van A")
                .dob("01/01/1990")
                .age("36")
                .gender("MALE")
                .address("")
                .encounterCode("ENC-001")
                .visitTime("28/07/2026 17:00")
                .doctorName("Doctor Test")
                .clinicalNotes("")
                .finalDiagnosis("")
                .aiResults(List.of(result))
                .build();
    }
}
