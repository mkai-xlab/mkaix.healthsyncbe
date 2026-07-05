package com.g93.be.controller;

import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.g93.be.repository.DicomInstanceRepository;
import java.util.List;
import com.g93.be.dto.DicomTagResponse;
import com.g93.be.dto.BatchDicomUploadResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.g93.be.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.g93.be.entity.DicomInstance;

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
    public ResponseEntity<BatchDicomUploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Received request to upload batch of {} DICOM files", files.size());
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files list is empty");
        }

        Long userId = userDetails != null && userDetails.getUser() != null ? userDetails.getUser().getId() : null;
        BatchDicomUploadResponse response = dicomService.uploadBatch(files, userId);
        log.info("Successfully processed batch upload. Errors: {}, Successful Patients: {}",
                response.getErrors().size(), response.getSuccessfulPatients().size());

        return ResponseEntity.ok(response);
    }

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @GetMapping("/instances/{id}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getInstanceImage(@PathVariable Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getStoragePngPath() != null) {
            String imagePath = instance.getStoragePngPath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "image/png")
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Failed to read image", e);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
