package com.g93.be.controller;

import com.g93.be.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/examinations")
@RequiredArgsConstructor
public class ReportController {

    private final PdfExportService pdfExportService;

    @PostMapping("/{id}/generate-report")
    @PreAuthorize("hasAuthority('GENERATE_PDF_REPORT')")
    public ResponseEntity<String> generatePdfReport(@PathVariable Long id) {
        String savedPath = pdfExportService.generateAndSavePdfReport(id);
        return ResponseEntity.ok("Report generated and saved at: " + savedPath);
    }
}
