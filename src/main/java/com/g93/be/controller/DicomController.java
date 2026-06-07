package com.g93.be.controller;

import com.g93.be.dto.DicomTagResponse;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    /**
     * Uploads a DICOM file and returns its extracted metadata.
     *
     * @param file The multipart DICOM file.
     * @return A list of extracted DICOM tags.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<List<DicomTagResponse>> uploadDicomFile(@RequestParam("file") MultipartFile file) {
        log.info("Received request to parse DICOM file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        
        List<DicomTagResponse> metadata = dicomService.extractMetadata(file);
        log.info("Successfully extracted {} tags from DICOM file.", metadata.size());
        
        return ResponseEntity.ok(metadata);
    }
}
