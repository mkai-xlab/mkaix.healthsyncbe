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
    public ResponseEntity<com.g93.be.dto.PatientDetailsResponse> uploadDicomFile(@RequestParam("file") MultipartFile file) {
        log.info("Received request to upload DICOM file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        
        com.g93.be.dto.PatientDetailsResponse response = dicomService.uploadAndProcessDicom(file);
        log.info("Successfully uploaded DICOM and created patient records.");
        
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    /**
     * Get image associated with a DICOM instance
     */
    @GetMapping("/instances/{id}/image")
    public ResponseEntity<Resource> getInstanceImage(@PathVariable Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dicom instance not found"));
                
        if (instance.getStoragePngPath() == null) {
            return ResponseEntity.notFound().build();
        }
        
        Path imagePath = Paths.get(instance.getStoragePngPath());
        if (!Files.exists(imagePath)) {
            return ResponseEntity.notFound().build();
        }
        
        Resource file = new FileSystemResource(imagePath.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(file);
    }
}
