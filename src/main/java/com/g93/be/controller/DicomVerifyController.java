package com.g93.be.controller;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
public class DicomVerifyController {

    private final DicomVerifyService dicomVerifyService;

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUploadSession(@RequestBody DicomVerifyRequest request, Principal principal) {
        List<Long> savedInstanceIds = dicomVerifyService.verifySession(request);
        
        if (savedInstanceIds != null && !savedInstanceIds.isEmpty()) {
            String username = (principal != null) ? principal.getName() : null;
            dicomVerifyService.processVerifiedSessionAsync(savedInstanceIds, username);
            
            return ResponseEntity.ok(java.util.Map.of("message", "Xác nhận thành công, hệ thống đang xử lý AI"));
        }
        
        return ResponseEntity.ok(java.util.Map.of("message", "Không có phiên ảnh nào được xác nhận"));
    }
}

