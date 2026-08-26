package com.g93.be.controller;

import com.g93.be.aspect.LogAction;
import com.g93.be.dto.GenerateReportRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.ReportDraftResponse;
import com.g93.be.dto.ReportListItemResponse;
import com.g93.be.dto.ReportResponse;
import com.g93.be.service.PdfExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final PdfExportService pdfExportService;

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ReportListItemResponse>> getGeneratedReports(
            Principal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pdfExportService.getGeneratedReports(pageable, principal.getName()));
    }

    /**
     * Returns the editable result text for an examination, pre-filled from the verified
     * Kellgren-Lawrence grades, so the doctor can review it before the PDF is rendered.
     */
    @GetMapping("/examinations/{id}/report-draft")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
    public ResponseEntity<ReportDraftResponse> getReportDraft(
            @PathVariable Long id,
            Principal principal) {
        return ResponseEntity.ok(pdfExportService.getReportDraft(id, principal.getName()));
    }

    /**
     * Generates the X-ray report PDF. The body is optional: omitting it keeps the auto-filled
     * result text, while sending it prints the doctor's own wording and re-renders the file.
     */
    @PostMapping("/examinations/{id}/generate-report")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
    public ResponseEntity<ReportResponse> generatePdfReport(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) GenerateReportRequest request,
            Principal principal) {
        return ResponseEntity.ok(
                pdfExportService.generateAndSavePdfReport(id, principal.getName(), request));
    }

    @GetMapping("/reports/{examinationId}/preview")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
    public ResponseEntity<Resource> previewReport(
            @PathVariable Long examinationId,
            Principal principal) {
        return fileResponse(
                pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), false);
    }

    @GetMapping("/reports/{examinationId}/download")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('EXPORT_DOWNLOAD_PDF'))")
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
