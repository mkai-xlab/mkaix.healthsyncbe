package com.g93.be.controller;
import com.g93.be.dto.PatientGradeStatsDto;



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
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    AiPredictionRequest aiRequest = new AiPredictionRequest(savedInstanceIds);
                    List<ExaminationDto> aiResultsList = aiService.predictBatch(aiRequest);
                    
                    // Calculate patient statistics based on max_predicted_grade for this specific batch
                    java.util.Map<Long, Integer> patientToMaxGrade = new java.util.HashMap<>();
                    for (ExaminationDto exam : aiResultsList) {
                        if (exam.getPatient() != null && exam.getMaxPredictedGrade() != null) {
                            Long patId = exam.getPatient().getId();
                            Integer currentMax = patientToMaxGrade.getOrDefault(patId, -1);
                            if (exam.getMaxPredictedGrade() > currentMax) {
                                patientToMaxGrade.put(patId, exam.getMaxPredictedGrade());
                            }
                        }
                    }

                    java.util.Map<Integer, Long> gradeCountMap = new java.util.HashMap<>();
                    for (Integer grade : patientToMaxGrade.values()) {
                        gradeCountMap.put(grade, gradeCountMap.getOrDefault(grade, 0L) + 1);
                    }

                    List<PatientGradeStatsDto> statsList = gradeCountMap.entrySet().stream()
                            .map(entry -> new PatientGradeStatsDto(entry.getKey(), entry.getValue()))
                            .collect(java.util.stream.Collectors.toList());
                    
                    // Send success notification
                    SendNotificationRequest notifReq = new SendNotificationRequest(
                            finalUserId,
                            "Phân tích AI hoàn tất",
                            "Hệ thống đã phân tích thành công hình ảnh X-Quang từ phiên xác nhận.",
                            "AI_RESULT",
                            statsList
                    );
                    notificationService.sendNotification(notifReq);

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
                }
            });
            
            return ResponseEntity.ok(java.util.Map.of("message", "Xác nhận thành công, hệ thống đang xử lý AI"));
        }
        
        return ResponseEntity.ok(java.util.Map.of("message", "Không có phiên ảnh nào được xác nhận"));
    }
}

