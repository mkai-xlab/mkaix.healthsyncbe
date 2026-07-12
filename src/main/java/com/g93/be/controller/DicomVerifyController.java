package com.g93.be.controller;

import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
public class DicomVerifyController {

    private final DicomVerifyService dicomVerifyService;

    @PostMapping("/verify")
    public ResponseEntity<String> verifyUploadSession(@RequestBody DicomVerifyRequest request) {
        dicomVerifyService.verifySession(request);
        return ResponseEntity.ok("Verification processed successfully.");
    }
}
