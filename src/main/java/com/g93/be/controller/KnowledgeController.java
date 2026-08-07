package com.g93.be.controller;

import com.g93.be.aspect.LogAction;
import com.g93.be.dto.KnowledgeBatchUploadResponse;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.dto.KnowledgeUrlRequest;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.service.KnowledgeBatchIngestionService;
import com.g93.be.service.KnowledgeIngestionService;
import com.g93.be.service.ReportKnowledgeSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/knowledge-documents")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeBatchIngestionService batchIngestionService;
    private final ReportKnowledgeSyncService reportKnowledgeSyncService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    @LogAction("UPLOAD_MEDICAL_KNOWLEDGE")
    public ResponseEntity<KnowledgeDocumentResponse> upload(
            @RequestPart MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "ALL") KnowledgeAccessScope accessScope,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ingestionService.upload(file, title, accessScope, principal.getName()));
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    @LogAction("UPLOAD_MEDICAL_KNOWLEDGE_BATCH")
    public ResponseEntity<KnowledgeBatchUploadResponse> uploadBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "ALL") KnowledgeAccessScope accessScope,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(batchIngestionService.upload(files, accessScope, principal.getName()));
    }

    @PostMapping("/url")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    @LogAction("ADD_MEDICAL_KNOWLEDGE_URL")
    public ResponseEntity<KnowledgeDocumentResponse> addUrl(
            @Valid @RequestBody KnowledgeUrlRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ingestionService.addUrl(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    public ResponseEntity<List<KnowledgeDocumentResponse>> getAll() {
        return ResponseEntity.ok(ingestionService.getAll());
    }

    @PostMapping("/{id}/reindex")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    @LogAction("REINDEX_MEDICAL_KNOWLEDGE")
    public ResponseEntity<KnowledgeDocumentResponse> reindex(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.reindex(id));
    }

    @PostMapping("/reports/{reportId}/sync")
    @PreAuthorize("hasAnyRole('DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('USE_AI_CHAT')")
    @LogAction("SYNC_REPORT_KNOWLEDGE")
    public ResponseEntity<KnowledgeDocumentResponse> syncReport(
            @PathVariable Long reportId, Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(reportKnowledgeSyncService.syncReport(reportId, principal.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
    @LogAction("DELETE_MEDICAL_KNOWLEDGE")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ingestionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
