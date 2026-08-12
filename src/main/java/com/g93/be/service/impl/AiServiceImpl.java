package com.g93.be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.ExaminationImageDto;


import com.g93.be.entity.DicomInstanceStatus;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.AiPredictionResultDto;
import com.g93.be.dto.FastApiPredictionResponse;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.mapper.ExaminationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private final DicomInstanceRepository dicomInstanceRepository;
    private final ExaminationRepository examinationRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiResultRepository aiResultRepository;
    private final AiResultConfidenceScoreRepository aiResultConfidenceScoreRepository;
    private final ImageRepository imageRepository;
    private final ExaminationMapper examinationMapper;
    private final NotificationService notificationService;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Value("${app.ai.api-url}")
    private String aiApiUrl;

    @Override
    @Transactional
    public List<ExaminationDto> predictBatch(AiPredictionRequest request) {
        // Cấu hình RestTemplate để giao tiếp HTTP với AI Server (Python/FastAPI)
        RestTemplate restTemplate = new RestTemplate();
        // Bản đồ lưu kết quả dự đoán của AI tương ứng với từng ID ảnh DICOM
        Map<Long, List<AiPredictionResultDto>> aiResultMap = new HashMap<>();
        // Cache danh sách các Ca Khám (Examination) để tổng hợp trạng thái chung sau cùng
        Map<Long, Examination> uniqueExams = new HashMap<>();
        Map<Long, List<DicomInstance>> instancesByExam = new HashMap<>();

        // Bước 1: Duyệt qua từng ảnh DICOM cần gửi đi phân tích
        for (Long instanceId : request.getDicomInstanceIds()) {
            Optional<DicomInstance> instanceOpt = dicomInstanceRepository.findById(instanceId);
            if (instanceOpt.isEmpty()) {
                throw new RuntimeException("DicomInstance not found for ID: " + instanceId);
            }

            DicomInstance instance = instanceOpt.get();
            if (instance.getStatus() != DicomInstanceStatus.AI_SENDING) {
                log.info("Skipping instance {} as its status is not AI_SENDING", instanceId);
                continue;
            }

            String pngPath = instance.getImage() != null ? instance.getImage().getFilePath() : null; // e.g.
                                                                                                     // /images/uuid.png
            if (pngPath == null) {
                throw new RuntimeException("Image/PNG path is NULL for instance ID: " + instanceId
                        + ". This means the DICOM to PNG conversion failed during upload.");
            }

            String safePngPath = pngPath;
            if (safePngPath.startsWith("/") || safePngPath.startsWith("\\")) {
                safePngPath = safePngPath.substring(1);
            }
            File imageFile = Paths.get(storageBaseDir, safePngPath).toFile();
            if (!imageFile.exists()) {
                throw new RuntimeException("Image file does not exist on disk: " + imageFile.getAbsolutePath());
            }

            try {
                // Bước 2: Đóng gói file ảnh PNG vào form 'multipart/form-data' để gửi cho AI
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(imageFile));

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                // Bắt đầu bấm giờ đo lường thời gian phản hồi của AI
                long startTime = System.currentTimeMillis();
                ResponseEntity<FastApiPredictionResponse> response = restTemplate.postForEntity(aiApiUrl, requestEntity,
                        FastApiPredictionResponse.class);
                long duration = System.currentTimeMillis() - startTime;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    FastApiPredictionResponse aiData = response.getBody();

                    // Bước 3: Phân rã dữ liệu từ AI gửi về
                    // 3.1 - Xử lý ảnh Annotated (Ảnh phác thảo có khoanh vùng bệnh - dạng chuỗi Base64)
                    String annotatedBase64 = aiData.getAnnotatedImage();
                    Image annotatedImageEntity = null;
                    if (annotatedBase64 != null) {
                        // Decode chuỗi Base64 thành file vật lý, lưu lên đĩa cứng và lưu Path vào Database
                        String annotatedPath = saveBase64ToDisk(annotatedBase64, "anno",
                                UUID.randomUUID().toString() + "_annotated.png");
                        if (annotatedPath != null) {
                            annotatedImageEntity = new Image();
                            annotatedImageEntity.setFilePath(annotatedPath);
                            annotatedImageEntity = imageRepository.save(annotatedImageEntity);
                            instance.setAnnotatedImage(annotatedImageEntity);
                        }
                    }

                    // 3.2 - Tạo mới hoặc ghi đè lịch sử phân tích (AiAnalysis)
                    AiAnalysis analysis = instance.getAiAnalysis();
                    if (analysis == null) {
                        analysis = new AiAnalysis();
                        analysis.setDicomInstance(instance);
                    } else {
                        // Nếu ảnh này đã được phân tích trước đó, xóa bỏ kết quả cũ để lưu kết quả mới
                        if (analysis.getAiResults() != null) {
                            aiResultRepository.deleteAll(analysis.getAiResults());
                            analysis.getAiResults().clear();
                        }
                    }
                    analysis.setStartTime(LocalDateTime.now());
                    analysis.setDuration(duration); // Ghi nhận AI mất bao nhiêu mili-giây để xử lý
                    analysis.setStatus("SUCCESS");
                    analysis = aiAnalysisRepository.save(analysis);

                    List<FastApiPredictionResponse.AiPredictionData> preds = aiData.getPredictions();
                    if (preds != null) {
                        for (FastApiPredictionResponse.AiPredictionData p : preds) {
                            // 3.3 - Giải mã và lưu file ảnh ROI (Vùng đầu gối cắt cúp)
                            Image roiImageEntity = null;
                            if (p.getRoiImage() != null) {
                                String roiPath = saveBase64ToDisk(p.getRoiImage(), "roi",
                                        UUID.randomUUID().toString() + "_roi.png");
                                if (roiPath != null) {
                                    roiImageEntity = new Image();
                                    roiImageEntity.setFilePath(roiPath);
                                    roiImageEntity = imageRepository.save(roiImageEntity);
                                }
                            }

                            // 3.4 - Giải mã và lưu file ảnh GradCAM (Bản đồ nhiệt độ mô phỏng lý do chẩn đoán)
                            Image gradcamImageEntity = null;
                            if (p.getGradcamImage() != null) {
                                String gradcamPath = saveBase64ToDisk(p.getGradcamImage(), "gradcam",
                                        UUID.randomUUID().toString() + "_gradcam.png");
                                if (gradcamPath != null) {
                                    gradcamImageEntity = new Image();
                                    gradcamImageEntity.setFilePath(gradcamPath);
                                    gradcamImageEntity = imageRepository.save(gradcamImageEntity);
                                }
                            }

                            AiResult aiResult = new AiResult();
                            aiResult.setAiAnalysis(analysis);
                            
                            Integer pGrade = p.getPredictedClass();
                            if (pGrade == null && p.getPredictedGrade() != null) {
                                try {
                                    String gradeStr = p.getPredictedGrade().replaceAll("[^0-9]", "");
                                    if (!gradeStr.isEmpty()) {
                                        pGrade = Integer.parseInt(gradeStr.substring(0, 1));
                                    }
                                } catch (Exception ignored) {}
                            }
                            if (pGrade == null) pGrade = 0; // ultimate fallback
                            
                            aiResult.setPredictedGrade(pGrade);
                            aiResult.setConfidence(p.getConfidence());
                            aiResult.setDescription(p.getDescription());
                            aiResult.setKneeSide(p.getKneeSide());
                            aiResult.setRoiImage(roiImageEntity);
                            aiResult.setGradcamImage(gradcamImageEntity);
                            if (gradcamImageEntity != null) {
                                aiResult.setStorageHeatmapFilePath(gradcamImageEntity.getFilePath()); // keep backward
                                                                                                      // compatibility
                            }
                            aiResult = aiResultRepository.save(aiResult);

                            // 3.5 - Lưu chi tiết tỷ lệ % (Confidence Scores) cho từng mức độ bệnh
                            if (p.getDetails() != null) {
                                AiResultConfidenceScore score = new AiResultConfidenceScore();
                                score.setAiResult(aiResult);
                                score.setC0Confidence(p.getDetails().getOrDefault("0Normal", 0.0));
                                score.setC1Confidence(p.getDetails().getOrDefault("1Doubtful", 0.0));
                                score.setC2Confidence(p.getDetails().getOrDefault("2Mild", 0.0));
                                score.setC3Confidence(p.getDetails().getOrDefault("3Moderate", 0.0));
                                score.setC4Confidence(p.getDetails().getOrDefault("4Severe", 0.0));
                                aiResultConfidenceScoreRepository.save(score);
                            }

                            // Build DTO
                            AiPredictionResultDto dto = AiPredictionResultDto.builder()
                                    .dicomInstanceId(instanceId)
                                    .aiAnalysisId(analysis.getId())
                                    .aiResultId(aiResult.getId())
                                    .predictedGrade(aiResult.getPredictedGrade())
                                    .effectiveGrade(aiResult.getPredictedGrade())
                                    .confidence(aiResult.getConfidence())
                                    .description(aiResult.getDescription())
                                    .details(p.getDetails())
                                    .kneeSide(aiResult.getKneeSide())
                                    .roiImageUrl(roiImageEntity != null ? "/api/v1/ai/roi/" + aiResult.getId() : null)
                                    .gradcamImageUrl(
                                            gradcamImageEntity != null ? "/api/v1/ai/heatmap/" + aiResult.getId()
                                                    : null)
                                    .annotatedImageUrl(
                                            annotatedImageEntity != null ? "/api/v1/ai/annotated/" + instanceId : null)
                                    .build();

                            aiResultMap.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(dto);
                        }
                    }

                    // Bước 4: Đánh dấu file ảnh này đã có kết quả (GET_RESULTED)
                    instance.setStatus(DicomInstanceStatus.GET_RESULTED);
                    dicomInstanceRepository.save(instance);

                    Examination exam = instance.getExamination();

                    if (exam != null) {
                        uniqueExams.putIfAbsent(exam.getId(), exam);
                        instancesByExam.computeIfAbsent(exam.getId(), k -> new ArrayList<>()).add(instance);
                    }
                } else {
                    log.error("Failed to get prediction from AI. Status: {}", response.getStatusCode());
                    throw new RuntimeException("AI API call failed with status: " + response.getStatusCode());
                }

            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                log.error("HTTP error during AI prediction for instance {}", instanceId, e);
                String errorMessage = e.getResponseBodyAsString();
                try {
                    JsonNode rootNode = new ObjectMapper().readTree(errorMessage);
                    if (rootNode.has("detail")) {
                        errorMessage = rootNode.get("detail").asText();
                    }
                } catch (Exception parseEx) {
                    // Ignore parse error, keep the original string
                }
                
                AiAnalysis analysis = instance.getAiAnalysis();
                if (analysis == null) {
                    analysis = new AiAnalysis();
                    analysis.setDicomInstance(instance);
                }
                analysis.setStartTime(LocalDateTime.now());
                analysis.setStatus("FAILED");
                if (errorMessage != null && errorMessage.length() > 500) {
                    errorMessage = errorMessage.substring(0, 497) + "...";
                }
                analysis.setErrorMessage(errorMessage);
                aiAnalysisRepository.save(analysis);

                instance.setStatus(DicomInstanceStatus.AI_FAILED);
                dicomInstanceRepository.save(instance);

                Examination exam = instance.getExamination();
                if (exam != null) {
                    uniqueExams.putIfAbsent(exam.getId(), exam);
                    instancesByExam.computeIfAbsent(exam.getId(), k -> new ArrayList<>()).add(instance);
                }
            } catch (Exception e) {
                log.error("Error during AI prediction for instance {}", instanceId, e);
                
                AiAnalysis analysis = instance.getAiAnalysis();
                if (analysis == null) {
                    analysis = new AiAnalysis();
                    analysis.setDicomInstance(instance);
                }
                analysis.setStartTime(LocalDateTime.now());
                analysis.setStatus("FAILED");
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 500) {
                    errorMsg = errorMsg.substring(0, 497) + "...";
                }
                analysis.setErrorMessage(errorMsg);
                aiAnalysisRepository.save(analysis);
                
                instance.setStatus(DicomInstanceStatus.AI_FAILED);
                dicomInstanceRepository.save(instance);
                
                Examination exam = instance.getExamination();
                if (exam != null) {
                    uniqueExams.putIfAbsent(exam.getId(), exam);
                    instancesByExam.computeIfAbsent(exam.getId(), k -> new ArrayList<>()).add(instance);
                }
            }
        }

        List<ExaminationDto> finalResults = new ArrayList<>();
        for (Examination exam : uniqueExams.values()) {
            List<DicomInstance> examInstances = instancesByExam.getOrDefault(exam.getId(), new ArrayList<>());
            
            boolean allFailed = true;
            for (DicomInstance inst : examInstances) {
                if (inst.getStatus() == DicomInstanceStatus.GET_RESULTED) {
                    allFailed = false;
                    break;
                }
            }
            
            if (allFailed && !examInstances.isEmpty()) {
                exam.setStatus(ExaminationStatus.AI_FAILED);
            } else {
                exam.setStatus(ExaminationStatus.NEED_VERIFY);
            }
            examinationRepository.save(exam);
            
            ExaminationDto examDto = examinationMapper.toDto(exam, examInstances);
            int maxGrade = -1;

            if (examDto != null && examDto.getImages() != null) {
                for (ExaminationImageDto img : examDto.getImages()) {
                    List<AiPredictionResultDto> aiResList = aiResultMap.get(img.getDicomInstanceId());
                    if (aiResList != null) {
                        img.setAiResults(aiResList);
                        for (AiPredictionResultDto r : aiResList) {
                            if (r.getPredictedGrade() != null && r.getPredictedGrade() > maxGrade) {
                                maxGrade = r.getPredictedGrade();
                            }
                        }
                    }
                }
                if (maxGrade >= 0) {
                    exam.setMaxPredictedGrade(maxGrade);
                    examDto.setMaxPredictedGrade(maxGrade);
                    examinationRepository.save(exam);
                }
                finalResults.add(examDto);
            }
        }

        // --- Bước 5: Bắn thông báo qua Web Socket cho Bác sĩ ---
        // Biến gộp thông tin: Bác sĩ -> Bệnh nhân -> Cấp độ bệnh nghiêm trọng nhất
        Map<Long, Map<Long, Integer>> maxGradeByPatientByDoctor = new HashMap<>();

        for (Examination exam : uniqueExams.values()) {
            if (exam.getDoctor() == null || exam.getPatient() == null)
                continue;
                
            if (exam.getStatus() == ExaminationStatus.AI_FAILED) {
                // Phân tích bị lỗi -> Bắn thông báo thất bại
                SendNotificationRequest req = new SendNotificationRequest(
                        exam.getDoctor().getId(),
                        "Lỗi phân tích AI",
                        "Ca khám " + exam.getPatient().getPatientCode() + " có lỗi khi phân tích AI. Vui lòng kiểm tra lại ảnh chụp.",
                        "ERROR",
                        null);
                notificationService.sendNotification(req);
            } else {
                Long doctorId = exam.getDoctor().getId();
                Long patientId = exam.getPatient().getId();
                int currentMax = exam.getMaxPredictedGrade() != null ? exam.getMaxPredictedGrade() : -1;
    
                maxGradeByPatientByDoctor
                        .computeIfAbsent(doctorId, k -> new HashMap<>())
                        .merge(patientId, currentMax, (a, b) -> Math.max(a, b));
            }
        }

        for (Map.Entry<Long, Map<Long, Integer>> entry : maxGradeByPatientByDoctor.entrySet()) {
            Long doctorId = entry.getKey();
            Map<Long, Integer> patientGrades = entry.getValue();

            int kl4 = 0, kl3 = 0, kl2 = 0, kl1 = 0;
            for (Integer grade : patientGrades.values()) {
                if (grade != null) {
                    if (grade == 4)
                        kl4++;
                    else if (grade == 3)
                        kl3++;
                    else if (grade == 2)
                        kl2++;
                    else if (grade == 1)
                        kl1++;
                }
            }

            int totalPatients = patientGrades.size();
            String message = String.format(
                    "Phân tích AI hoàn tất cho %d bệnh nhân. Chi tiết: %d Bệnh Nhân mắc KL4, %d Bệnh Nhân mắc KL3, %d Bệnh Nhân mắc KL2, %d Bệnh Nhân mắc KL1.",
                    totalPatients, kl4, kl3, kl2, kl1);

            SendNotificationRequest req = new SendNotificationRequest(
                    doctorId,
                    "Thống kê kết quả AI",
                    message,
                    "INFO",
                    null);
            notificationService.sendNotification(req);
        }

        return finalResults;
    }

    /**
     * Hàm Utility: Dùng để chuyển đổi ảnh dạng Base64 (Chuỗi mã hóa) thành file tĩnh PNG
     */
    private String saveBase64ToDisk(String base64String, String subDir, String fileName) {
        if (base64String == null || !base64String.startsWith("data:image"))
            return null;
            
        // Loại bỏ tiền tố (VD: 'data:image/png;base64,') để lấy data nhị phân thực sự
        String[] parts = base64String.split(",");
        if (parts.length != 2)
            return null;

        try {
            // Giải mã ngược từ Base64 về dạng byte[]
            byte[] decodedImg = Base64.getDecoder().decode(parts[1]);
            String filePath = "/images/" + subDir + "/" + fileName;
            
            // Tìm ổ đĩa thực tế để lưu (VD: D:/Capstone/data/images/roi/abc.png)
            Path targetPath = Paths.get(storageBaseDir, "images", subDir, fileName);
            targetPath.getParent().toFile().mkdirs(); // Đảm bảo thư mục đã tồn tại
            
            // Tiến hành ghi data ra file
            try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                fos.write(decodedImg);
            }
            return filePath;
        } catch (Exception e) {
            log.error("Failed to decode and save base64 image", e);
            return null;
        }
    }

    @Override
    public org.springframework.core.io.Resource getHeatmapImageResource(Long aiResultId) {
        AiResult result = aiResultRepository.findById(aiResultId).orElse(null);
        if (result != null && result.getStorageHeatmapFilePath() != null) {
            String imagePath = result.getStorageHeatmapFilePath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                }
            } catch (Exception e) {
                log.error("Failed to read heatmap image for result id: {}", aiResultId, e);
            }
        }
        return null;
    }
}

