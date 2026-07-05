package com.g93.be.controller;

import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.entity.DicomInstance;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import com.g93.be.dto.DicomTagResponse;

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
    public ResponseEntity<com.g93.be.dto.BatchDicomUploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files) {
        log.info("Received request to upload batch of {} DICOM files", files.size());
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files list is empty");
        }

        com.g93.be.dto.BatchDicomUploadResponse response = dicomService.uploadBatch(files);
        log.info("Successfully processed batch upload. Errors: {}, Successful Patients: {}",
                response.getErrors().size(), response.getSuccessfulPatients().size());

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload/zip-batch", consumes = "multipart/form-data")
    public ResponseEntity<java.util.Map<String, String>> uploadZipBatch(
            @RequestParam("file") MultipartFile file) {
        log.info("Received request to upload ZIP batch DICOM file: {}", file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            java.util.Map<String, String> errResponse = new java.util.HashMap<>();
            errResponse.put("error", "Invalid file format. Only .zip files are allowed for batch upload.");
            errResponse.put("status", "FAILED");
            return ResponseEntity.badRequest().body(errResponse);
        }

        try {
            Path tempZipFile = Files.createTempFile("main_batch_", ".zip");
            file.transferTo(tempZipFile.toFile());
            
            // Spawn background task
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                dicomService.processZipBatch(tempZipFile);
            });
            
            java.util.Map<String, String> response = new java.util.HashMap<>();
            response.put("message", "ZIP file accepted. Processing in background.");
            response.put("status", "PROCESSING");
            
            return ResponseEntity.ok(response);
        } catch (java.io.IOException e) {
            log.error("Failed to save uploaded ZIP file", e);
            throw new RuntimeException("Failed to save uploaded ZIP file", e);
        }
    }

    @org.springframework.beans.factory.annotation.Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @GetMapping("/instances/{id}/image")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<org.springframework.core.io.Resource> getInstanceImage(@PathVariable Long id) {
        com.g93.be.entity.DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getStoragePngPath() != null) {
            String imagePath = instance.getStoragePngPath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                java.nio.file.Path path = java.nio.file.Paths.get(storageBaseDir, relPath);
                org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "image/png")
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Failed to read image", e);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
