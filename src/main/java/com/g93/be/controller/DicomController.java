package com.g93.be.controller;
import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.dto.DicomTagResponse;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Controller for DICOM file operations.
 */
@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
@Slf4j
public class DicomController {

    private final DicomService dicomService;
    private final DicomInstanceRepository dicomInstanceRepository;

    /**
     * Uploads a DICOM file and returns its extracted metadata.
     *
     * @param file The multipart DICOM file.
     * @return A list of extracted DICOM tags.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    // @PreAuthorize("hasAuthority('UPLOAD_DICOM_IMAGE')")
    public ResponseEntity<List<DicomTagResponse>> uploadDicomFile(@RequestParam("file") MultipartFile file) {
        log.info("Received request to upload DICOM file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        List<DicomTagResponse> metadata = dicomService.extractMetadata(file);
        log.info("Successfully extracted {} tags from DICOM file.", metadata.size());

        return ResponseEntity.ok(metadata);
    }

    @PostMapping(value = "/upload/batch", consumes = "multipart/form-data")
    // @PreAuthorize("hasAuthority('UPLOAD_DICOM_IMAGE')")
    public ResponseEntity<BatchDicomUploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            java.security.Principal principal) {
        log.info("Received request to upload batch of {} DICOM files", files.size());
        String username = (principal != null) ? principal.getName() : null;
        BatchDicomUploadResponse response = dicomService.uploadBatchFiles(files, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload/zip-batch", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('UPLOAD_DICOM_IMAGE')")
    public ResponseEntity<?> uploadZipBatch(
            @RequestParam("file") MultipartFile file,
            java.security.Principal principal) {
        log.info("Received request to upload ZIP batch DICOM file");
        try {
            String username = (principal != null) ? principal.getName() : null;
            BatchDicomUploadResponse response = dicomService.uploadZipBatchFile(file, username);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            java.util.Map<String, String> errResponse = new java.util.HashMap<>();
            errResponse.put("error", e.getMessage());
            errResponse.put("status", "FAILED");
            return ResponseEntity.badRequest().body(errResponse);
        }
    }



    @GetMapping("/total-studies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getTotalStudies() {
        log.info("Received request to get total unique DICOM studies");
        return ResponseEntity.ok(dicomInstanceRepository.countUniqueStudies());
    }

    /**
     * Retrieves the JSON string of the upload session from Redis.
     */
    @GetMapping(value = "/upload-session/{sessionId}", produces = "application/json")
    public ResponseEntity<String> getUploadSession(@PathVariable String sessionId) {
        log.info("Received request to get upload session: {}", sessionId);
        String sessionJson = dicomService.getUploadSession(sessionId);
        if (sessionJson == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(sessionJson);
    }

    @GetMapping("/instances/{id}/image")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VIEW_IMAGE_LIST')")
    public ResponseEntity<Resource> getInstanceImage(@PathVariable Long id) {
        Resource resource = dicomService.getInstanceImageResource(id);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/instances/{id}/raw")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VIEW_IMAGE_LIST')")
    public ResponseEntity<Resource> getInstanceRaw(@PathVariable Long id) {
        Resource resource = dicomService.getInstanceRawResource(id);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dicom_file.dcm\"")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }
}

