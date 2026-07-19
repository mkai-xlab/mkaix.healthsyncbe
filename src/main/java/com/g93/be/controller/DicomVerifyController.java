package com.g93.be.controller;


import com.g93.be.entity.User;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import com.g93.be.repository.UserRepository;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.entity.User;
import com.g93.be.dto.ExaminationDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
public class DicomVerifyController {

    private final DicomVerifyService dicomVerifyService;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUploadSession(@RequestBody DicomVerifyRequest request, Principal principal) {
        List<Long> savedInstanceIds = dicomVerifyService.verifySession(request);
        
        Long userId = null;
        if (principal != null && principal.getName() != null) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        final Long finalUserId = (userId != null) ? userId : 1L;

        if (savedInstanceIds != null && !savedInstanceIds.isEmpty()) {
            try {
                AiPredictionRequest aiRequest = new AiPredictionRequest(savedInstanceIds);
                List<ExaminationDto> aiResultsList = aiService.predictBatch(aiRequest);
                
                // Send success notification
                SendNotificationRequest notifReq = new SendNotificationRequest(
                        finalUserId,
                        "Phân tích AI hoàn tất",
                        "Hệ thống đã phân tích thành công hình ảnh X-Quang từ phiên xác nhận.",
                        "AI_RESULT",
                        null
                );
                notificationService.sendNotification(notifReq);

                return ResponseEntity.ok(aiResultsList);
            } catch (Exception e) {
                e.printStackTrace();
                // Send error notification
                try {
                    SendNotificationRequest errReq = new SendNotificationRequest(
                            finalUserId,
                            "Lỗi phân tích AI",
                            "Đã có lỗi xảy ra trong quá trình phân tích AI.",
                            "ERROR",
                            null
                    );
                    notificationService.sendNotification(errReq);
                } catch (Exception ignored) {}

                return ResponseEntity.status(500).body("Error processing AI prediction: " + e.getMessage());
            }
        }
        
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
