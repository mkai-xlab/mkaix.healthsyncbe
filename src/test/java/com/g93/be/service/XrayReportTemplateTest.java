package com.g93.be.service;

import com.g93.be.dto.XrayReportDataDto;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrayReportTemplateTest {

    @Test
    void xrayReportTemplateRendersEveryFormBlockToPdf() throws Exception {
        String html = renderHtml(reportData());

        byte[] pdf = renderPdf(html);
        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            // Letter-spaced headings extract with a space between glyphs, so match them unspaced.
            String unspaced = text.replaceAll("\\s+", "");
            assertTrue(unspaced.contains("PHIẾUCHỤPXQUANG"), "Form title is missing");
            assertTrue(text.contains("VIỆN Y HỌC CỔ TRUYỀN QUÂN ĐỘI"), "Letterhead is missing");
            assertTrue(text.contains("08/BV-02"), "Form code is missing");
            assertTrue(text.contains("HÀ HUY ĐOÀN"), "Patient name is missing");
            assertTrue(unspaced.contains("KẾTQUẢ"), "Result heading is missing");
            assertTrue(text.contains("Gối phải: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence)."),
                    "Findings are missing");
            assertTrue(unspaced.contains("KẾTLUẬN"), "Conclusion heading is missing");
            assertTrue(text.contains("Hình ảnh thoái hóa khớp gối hai bên"), "Conclusion is missing");
            assertTrue(unspaced.contains("BÁCSỸCHUYÊNKHOA"), "Signature role is missing");
            assertTrue(text.contains("Hà Công Thỏa"), "Signing doctor is missing");
            assertTrue(text.contains("hệ thống HealthSync"), "Footer attribution is missing");
            assertWatermarkVisibleOnEveryPage(document);
        }
    }

    @Test
    void doctorEditedResultTextReplacesTheAutoFilledDraft() throws Exception {
        XrayReportDataDto data = reportData();
        data.setFindings(List.of("Bác sĩ mô tả lại kết quả bằng tay."));
        data.setConclusionLines(List.of("Kết luận bác sĩ tự nhập.", "Hẹn tái khám sau 3 tháng."));

        try (PDDocument document = PDDocument.load(renderPdf(renderHtml(data)))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Bác sĩ mô tả lại kết quả bằng tay."));
            assertTrue(text.contains("Kết luận bác sĩ tự nhập."));
            assertTrue(text.contains("Hẹn tái khám sau 3 tháng."));
            assertFalse(text.contains("Gối phải"), "Auto-filled findings should no longer be printed");
        }
    }

    @Test
    void thePackagedHospitalCrestIsEmbeddedInThePdf() throws Exception {
        XrayReportDataDto data = reportData();
        data.setHospitalLogo(logoDataUri("report/logo-hospital.png"));

        String html = renderHtml(data);
        assertTrue(html.contains("data:image/png;base64,"), "Logo bytes must travel inside the HTML");

        try (PDDocument document = PDDocument.load(renderPdf(html))) {
            // Only the hospital crest: the form carries no lab branding mark.
            assertEquals(1, countImages(document), "The hospital crest should be drawn");
        }
    }

    @Test
    void aMissingLogoDegradesToALogoLessLetterheadInsteadOfFailing() throws Exception {
        XrayReportDataDto data = reportData();
        data.setHospitalLogo(null);

        try (PDDocument document = PDDocument.load(renderPdf(renderHtml(data)))) {
            assertEquals(0, countImages(document));
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("VIỆN Y HỌC CỔ TRUYỀN QUÂN ĐỘI"), "Letterhead text must survive");
        }
    }

    @Test
    void blankFormFieldsRenderAsEmptyLinesInsteadOfFailing() throws Exception {
        XrayReportDataDto data = reportData();
        data.setAddress("");
        data.setAttemptNumber("");

        try (PDDocument document = PDDocument.load(renderPdf(renderHtml(data)))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Địa chỉ"));
            assertTrue(text.contains("Khoa"));
        }
    }

    @Test
    void theFormNoLongerPrintsTheDroppedRequestFields() throws Exception {
        try (PDDocument document = PDDocument.load(renderPdf(renderHtml(reportData())))) {
            String text = new PDFTextStripper().getText(document);
            assertFalse(text.contains("Buồng/PK"), "Room row should be gone");
            assertFalse(text.contains("Giường"), "Bed row should be gone");
            assertFalse(text.contains("Yêu cầu kiểm tra"), "Exam request row should be gone");
            // The diagnosis row cannot be asserted by text: "Chẩn đoán" legitimately appears
            // inside the department name. Its removal is enforced by the DTO no longer
            // carrying the field, which the compiler checks.
            assertFalse(text.contains("Bác sĩ điều trị"), "Referring physician block should be gone");
            assertFalse(text.contains("MKAI"), "Lab branding should be gone from the form");
        }
    }

    private String logoDataUri(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        assertTrue(resource.exists(), location + " must be packaged");
        try (java.io.InputStream inputStream = resource.getInputStream()) {
            return "data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
    }

    private int countImages(PDDocument document) {
        int count = 0;
        for (org.apache.pdfbox.pdmodel.PDPage page : document.getPages()) {
            for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames()) {
                if (page.getResources().isImageXObject(name)) {
                    count++;
                }
            }
        }
        return count;
    }

    private String renderHtml(XrayReportDataDto data) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("data", data);
        return templateEngine.process("pdf/xray-report-template", context);
    }

    private byte[] renderPdf(String html) throws Exception {
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
        return output.toByteArray();
    }

    private void assertWatermarkVisibleOnEveryPage(PDDocument document) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            BufferedImage page = renderer.renderImageWithDPI(pageIndex, 72);
            int watermarkPixels = 0;
            for (int y = 0; y < page.getHeight(); y++) {
                for (int x = 0; x < page.getWidth(); x++) {
                    int rgb = page.getRGB(x, y);
                    int red = (rgb >> 16) & 0xff;
                    int green = (rgb >> 8) & 0xff;
                    int blue = rgb & 0xff;
                    if (red > 170 && green - red > 3 && blue - red > 8) {
                        watermarkPixels++;
                    }
                }
            }
            assertTrue(watermarkPixels > 500, "Watermark is not visible on PDF page " + (pageIndex + 1));
        }
    }

    private XrayReportDataDto reportData() {
        return XrayReportDataDto.builder()
                .ministryName("BỘ QUỐC PHÒNG")
                .hospitalName("VIỆN Y HỌC CỔ TRUYỀN QUÂN ĐỘI")
                .departmentName("KHOA CHẨN ĐOÁN HÌNH ẢNH")
                .formCode("08/BV-02")
                .documentNumber("ENC-2026-0007")
                .attemptNumber("")
                .patientName("HÀ HUY ĐOÀN")
                .age("48")
                .gender("Nam")
                .address("Xã Quang Bị, Thành phố Hà Nội")
                .clinicalDepartment("Khoa Chẩn đoán hình ảnh")
                .findings(List.of(
                        "Gối phải: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence).",
                        "Gối trái: Thoái hóa khớp gối độ 3 (Kellgren-Lawrence)."))
                .conclusionLines(List.of(
                        "Hình ảnh thoái hóa khớp gối hai bên độ 3 theo phân loại Kellgren-Lawrence."))
                .signaturePlace("Hà Nội")
                .signatureDay("07")
                .signatureMonth("05")
                .signatureYear("2026")
                .doctorName("Hà Công Thỏa")
                .build();
    }
}
