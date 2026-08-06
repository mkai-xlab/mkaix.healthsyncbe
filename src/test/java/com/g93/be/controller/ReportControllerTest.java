package com.g93.be.controller;

import com.g93.be.service.PdfExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private PdfExportService pdfExportService;

    @InjectMocks
    private ReportController reportController;

    @Test
    void previewReturnsPdfInline() {
        when(pdfExportService.getReportFileByExaminationId(9L, "doctor"))
                .thenReturn(reportFile());

        ResponseEntity<?> response = reportController.previewReport(9L, () -> "doctor");

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(3L, response.getHeaders().getContentLength());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith("inline"));
        verify(pdfExportService).getReportFileByExaminationId(9L, "doctor");
    }

    @Test
    void downloadReturnsSamePdfAsAttachment() {
        when(pdfExportService.getReportFileByExaminationId(9L, "doctor"))
                .thenReturn(reportFile());

        ResponseEntity<?> response = reportController.downloadReport(9L, () -> "doctor");

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith("attachment"));
        verify(pdfExportService).getReportFileByExaminationId(9L, "doctor");
    }

    private PdfExportService.ReportFile reportFile() {
        return new PdfExportService.ReportFile(
                new ByteArrayResource(new byte[]{1, 2, 3}),
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                3L);
    }
}
