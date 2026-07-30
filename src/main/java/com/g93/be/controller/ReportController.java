package com.g93.be.controller;

import com.g93.be.aspect.LogAction;
import com.g93.be.dto.ReportResponse;
import com.g93.be.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final PdfExportService pdfExportService;

    @PostMapping("/examinations/{id}/generate-report")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or hasAuthority('GENERATE_PDF_REPORT')")
    public ResponseEntity<ReportResponse> generatePdfReport(
            @PathVariable Long id,
            Principal principal) {
        return ResponseEntity.ok(
                pdfExportService.generateAndSavePdfReport(id, principal.getName()));
    }

    @GetMapping("/reports/{examinationId}/preview")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or hasAuthority('GENERATE_PDF_REPORT')")
    public ResponseEntity<Resource> previewReport(
            @PathVariable Long examinationId,
            Principal principal) {
        return fileResponse(
                pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), false);
    }

    @GetMapping("/reports/{examinationId}/download")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or hasAuthority('EXPORT_DOWNLOAD_PDF')")
    @LogAction("DOWNLOAD_PDF_REPORT")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable Long examinationId,
            Principal principal) {
        return fileResponse(
                pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), true);
    }

    private ResponseEntity<Resource> fileResponse(
            PdfExportService.ReportFile reportFile,
            boolean download) {
        ContentDisposition disposition = (download
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(reportFile.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(reportFile.contentType()))
                .contentLength(reportFile.fileSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(reportFile.resource());
    }
}
