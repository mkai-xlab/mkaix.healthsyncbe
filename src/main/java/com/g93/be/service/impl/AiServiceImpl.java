package com.g93.be.service.impl;
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

    @Value("${app.ai.api-url:http://54.254.113.71:8005/api/v1/predict}")
    private String aiApiUrl;

    @Override
    @Transactional
    public List<ExaminationDto> predictBatch(AiPredictionRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        Map<Long, List<AiPredictionResultDto>> aiResultMap = new HashMap<>();
        Map<Long, Examination> uniqueExams = new HashMap<>();
        Map<Long, List<DicomInstance>> instancesByExam = new HashMap<>();

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
                // Call external API
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(imageFile));

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                long startTime = System.currentTimeMillis();
                ResponseEntity<FastApiPredictionResponse> response = restTemplate.postForEntity(aiApiUrl, requestEntity,
                        FastApiPredictionResponse.class);
                long duration = System.currentTimeMillis() - startTime;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    FastApiPredictionResponse aiData = response.getBody();

                    // Decode base64 Annotated Image
                    String annotatedBase64 = aiData.getAnnotatedImage();
                    Image annotatedImageEntity = null;
                    if (annotatedBase64 != null) {
                        String annotatedPath = saveBase64ToDisk(annotatedBase64,
                                UUID.randomUUID().toString() + "_annotated.png");
                        if (annotatedPath != null) {
                            annotatedImageEntity = new Image();
                            annotatedImageEntity.setFilePath(annotatedPath);
                            annotatedImageEntity = imageRepository.save(annotatedImageEntity);
                            instance.setAnnotatedImage(annotatedImageEntity);
                        }
                    }

                    // Save AiAnalysis
                    AiAnalysis analysis = new AiAnalysis();
                    analysis.setDicomInstance(instance);
                    analysis.setStartTime(LocalDateTime.now());
                    analysis.setDuration(duration);
                    analysis.setStatus("SUCCESS");
                    analysis = aiAnalysisRepository.save(analysis);

                    List<FastApiPredictionResponse.AiPredictionData> preds = aiData.getPredictions();
                    if (preds != null) {
                        for (FastApiPredictionResponse.AiPredictionData p : preds) {
                            // Decode ROI
                            Image roiImageEntity = null;
                            if (p.getRoiImage() != null) {
                                String roiPath = saveBase64ToDisk(p.getRoiImage(),
                                        UUID.randomUUID().toString() + "_roi.png");
                                if (roiPath != null) {
                                    roiImageEntity = new Image();
                                    roiImageEntity.setFilePath(roiPath);
                                    roiImageEntity = imageRepository.save(roiImageEntity);
                                }
                            }

                            // Decode GradCAM
                            Image gradcamImageEntity = null;
                            if (p.getGradcamImage() != null) {
                                String gradcamPath = saveBase64ToDisk(p.getGradcamImage(),
                                        UUID.randomUUID().toString() + "_gradcam.png");
                                if (gradcamPath != null) {
                                    gradcamImageEntity = new Image();
                                    gradcamImageEntity.setFilePath(gradcamPath);
                                    gradcamImageEntity = imageRepository.save(gradcamImageEntity);
                                }
                            }

                            // Save AiResult
                            AiResult aiResult = new AiResult();
                            aiResult.setAiAnalysis(analysis);
                            aiResult.setPredictedGrade(p.getPredictedClass());
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

                            // Save Confidence Scores
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

                    // Update Examination Status and DicomInstance Status
                    instance.setStatus(DicomInstanceStatus.GET_RESULTED);
                    dicomInstanceRepository.save(instance);

                    Examination exam = instance.getExamination();
                    if (exam != null) {
                        exam.setStatus(ExaminationStatus.NEED_VERIFY);
                        examinationRepository.save(exam);
                    }

                    if (exam != null) {
                        uniqueExams.putIfAbsent(exam.getId(), exam);
                        instancesByExam.computeIfAbsent(exam.getId(), k -> new ArrayList<>()).add(instance);
                    }
                } else {
                    log.error("Failed to get prediction from AI. Status: {}", response.getStatusCode());
                    throw new RuntimeException("AI API call failed with status: " + response.getStatusCode());
                }

            } catch (Exception e) {
                log.error("Error during AI prediction for instance {}", instanceId, e);
                throw new RuntimeException("KhÃ´ng thá»ƒ káº¿t ná»‘i Ä‘áº¿n Server AI: " + e.getMessage(), e);
            }
        }

        List<ExaminationDto> finalResults = new ArrayList<>();
        for (Examination exam : uniqueExams.values()) {
            List<DicomInstance> examInstances = instancesByExam.getOrDefault(exam.getId(), new ArrayList<>());
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

        // --- WebSocket Notification Logic ---
        Map<Long, Map<Long, Integer>> maxGradeByPatientByDoctor = new HashMap<>();

        for (Examination exam : uniqueExams.values()) {
            if (exam.getDoctor() == null || exam.getPatient() == null)
                continue;
            Long doctorId = exam.getDoctor().getId();
            Long patientId = exam.getPatient().getId();
            int currentMax = exam.getMaxPredictedGrade() != null ? exam.getMaxPredictedGrade() : -1;

            maxGradeByPatientByDoctor
                    .computeIfAbsent(doctorId, k -> new HashMap<>())
                    .merge(patientId, currentMax, (a, b) -> Math.max(a, b));
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
                    "PhÃ¢n tÃ­ch AI hoÃ n táº¥t cho %d bá»‡nh nhÃ¢n. Chi tiáº¿t: %d Bá»‡nh NhÃ¢n máº¯c KL4, %d Bá»‡nh NhÃ¢n máº¯c KL3, %d Bá»‡nh NhÃ¢n máº¯c KL2, %d Bá»‡nh NhÃ¢n máº¯c KL1.",
                    totalPatients, kl4, kl3, kl2, kl1);

            SendNotificationRequest req = new SendNotificationRequest(
                    doctorId,
                    "Thá»‘ng kÃª káº¿t quáº£ AI",
                    message,
                    "INFO",
                    null);
            notificationService.sendNotification(req);
        }

        return finalResults;
    }

    private String saveBase64ToDisk(String base64String, String fileName) {
        if (base64String == null || !base64String.startsWith("data:image"))
            return null;
        String[] parts = base64String.split(",");
        if (parts.length != 2)
            return null;

        try {
            byte[] decodedImg = Base64.getDecoder().decode(parts[1]);
            String filePath = "/images/" + fileName;
            Path targetPath = Paths.get(storageBaseDir, "images", fileName);
            targetPath.getParent().toFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                fos.write(decodedImg);
            }
            return filePath;
        } catch (Exception e) {
            log.error("Failed to decode and save base64 image", e);
            return null;
        }
    }
}

