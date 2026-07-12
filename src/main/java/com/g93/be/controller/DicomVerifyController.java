package com.g93.be.controller;

import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.service.AiService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
public class DicomVerifyController {

    private final DicomVerifyService dicomVerifyService;
    private final AiService aiService;

    @PostMapping("/verify")
    public ResponseEntity<String> verifyUploadSession(@RequestBody DicomVerifyRequest request) {
        List<Long> savedInstanceIds = dicomVerifyService.verifySession(request);
        
        if (savedInstanceIds != null && !savedInstanceIds.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    AiPredictionRequest aiRequest = new AiPredictionRequest(savedInstanceIds);
                    aiService.predictBatch(aiRequest);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        return ResponseEntity.ok("Verification processed successfully.");
    }
}
