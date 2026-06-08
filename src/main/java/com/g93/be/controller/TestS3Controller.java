package com.g93.be.controller;

import com.g93.be.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
@Slf4j
public class TestS3Controller {

    private final StorageService storageService;

    /**
     * Endpoint to test uploading a real file (image) to S3.
     */
    @PostMapping(value = "/test-upload", consumes = "multipart/form-data")
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
