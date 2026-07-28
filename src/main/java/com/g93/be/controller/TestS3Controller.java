package com.g93.be.controller;

import com.g93.be.service.StorageService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/s3")
@Slf4j
public class TestS3Controller {

    private final StorageService storageService;

    public TestS3Controller(@org.springframework.beans.factory.annotation.Qualifier("s3StorageServiceImpl") StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Endpoint to test uploading a real file (image) to S3.
     */
    @PostMapping(value = "/test-upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testUpload(
            @RequestParam("folderName") String folderName,
            @RequestParam("fileName") String fileName,
            @RequestParam("file") MultipartFile file) {

        log.info("Received request to upload real file to folder: {}, fileName: {}, size: {}",
                folderName, fileName, file.getSize());

        String result = storageService.uploadFile(folderName, fileName, file);
        return ResponseEntity.ok(result);
    }
}
