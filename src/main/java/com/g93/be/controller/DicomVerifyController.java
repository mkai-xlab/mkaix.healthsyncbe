package com.g93.be.controller;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
public class DicomVerifyController {

    private final DicomVerifyService dicomVerifyService;
    private final UserRepository userRepository;

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('UPLOAD_DICOM_IMAGE') and hasAuthority('TRIGGER_AI_ANALYSIS'))")
    public ResponseEntity<?> verifyUploadSession(@RequestBody DicomVerifyRequest request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated user was not found");
        }
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Authenticated user was not found"));
        String roleCode = user.getRole() == null ? null : user.getRole().getCode();
        boolean privilegedUser = "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)
                || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode);
        List<Long> savedInstanceIds = dicomVerifyService.verifySession(request, user.getId(), privilegedUser);
        if (savedInstanceIds != null && !savedInstanceIds.isEmpty()) {
            String username = (principal != null) ? principal.getName() : null;
            dicomVerifyService.processVerifiedSessionAsync(savedInstanceIds, username);
            
            return ResponseEntity.ok(java.util.Map.of("message", "Xác nhận thành công, hệ thống đang xử lý AI"));
        }
        
        return ResponseEntity.ok(java.util.Map.of("message", "Không có phiên ảnh nào được xác nhận"));
    }
}

