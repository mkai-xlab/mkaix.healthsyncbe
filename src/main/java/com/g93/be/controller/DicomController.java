package com.g93.be.controller;


import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.User;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.g93.be.repository.DicomInstanceRepository;
import java.util.List;
import com.g93.be.dto.DicomTagResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.g93.be.entity.DicomInstance;
import org.springframework.security.access.prepost.PreAuthorize;

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
    private final com.g93.be.repository.UserRepository userRepository;

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
    public ResponseEntity<java.util.Map<String, String>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            java.security.Principal principal) {
        log.info("Received request to upload batch of {} DICOM files", files.size());
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files list is empty");
        }

        Long userId = null;
        if (principal != null && principal.getName() != null) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        if (userId == null) {
            userId = 1L; // Temporary bypass for testing
        }
        
        try {
            // Save files to temp directory for async processing
            java.util.Map<String, Path> tempFilePaths = new java.util.LinkedHashMap<>();
            for (MultipartFile file : files) {
                if (file.getOriginalFilename() != null) {
                    Path tempFile = Files.createTempFile("main_batch_", ".dcm");
                    file.transferTo(tempFile.toFile());
                    tempFilePaths.put(file.getOriginalFilename(), tempFile);
                }
            }

            // Spawn background task
            final Long finalUserId = userId;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                dicomService.processBatchPaths(tempFilePaths, finalUserId);
            });

            java.util.Map<String, String> response = new java.util.HashMap<>();
            response.put("message", "DICOM files accepted. Processing in background.");
            response.put("status", "PROCESSING");

            return ResponseEntity.ok(response);
        } catch (java.io.IOException e) {
            log.error("Failed to save uploaded DICOM files for background processing", e);
            throw new RuntimeException("Failed to save uploaded DICOM files", e);
        }
    }

    @PostMapping(value = "/upload/zip-batch", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('UPLOAD_DICOM_IMAGE')")
    public ResponseEntity<java.util.Map<String, String>> uploadZipBatch(
            @RequestParam("file") MultipartFile file,
            java.security.Principal principal) {
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

        Long userId = null;
        if (principal != null && principal.getName() != null) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        if (userId == null) {
            java.util.Map<String, String> err = new java.util.HashMap<>();
            err.put("error", "Unauthorized: Valid access token is required");
            err.put("status", "FAILED");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(err);
        }

        try {
            Path tempZipFile = Files.createTempFile("main_batch_", ".zip");
            file.transferTo(tempZipFile.toFile());
            
            // Spawn background task
            final Long finalUserId = userId;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                dicomService.processZipBatch(tempZipFile, finalUserId);
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
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VIEW_IMAGE_LIST')")
    public ResponseEntity<Resource> getInstanceImage(@PathVariable Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getImage() != null && instance.getImage().getFilePath() != null) {
            String imagePath = instance.getImage().getFilePath();
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

    @GetMapping("/instances/{id}/raw")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VIEW_IMAGE_LIST')")
    public ResponseEntity<Resource> getInstanceRaw(@PathVariable Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getDicomRaw() != null && instance.getDicomRaw().getFilePath() != null) {
            String rawPath = instance.getDicomRaw().getFilePath();
            try {
                String relPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName().toString() + "\"")
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Failed to read raw dicom", e);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
