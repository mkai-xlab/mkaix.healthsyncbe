package com.g93.be.service.impl;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.AiPredictionResultDto;
import com.g93.be.dto.FastApiPredictionResponse;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.service.AiService;
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
    private final ExaminationMapper examinationMapper;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;
    
    private final String AI_API_URL = "http://54.254.113.71:8005/api/v1/predict";

    @Override
    @Transactional
    public List<ExaminationDto> predictBatch(AiPredictionRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        Map<Long, List<AiPredictionResultDto>> aiResultMap = new HashMap<>();
        Map<Long, Examination> uniqueExams = new HashMap<>();
        Map<Long, List<DicomInstance>> instancesByExam = new HashMap<>();

        for (Long instanceId : request.getDicomInstanceIds()) {
            Optional<DicomInstance> instanceOpt = dicomInstanceRepository.findById(instanceId);
            if (instanceOpt.isEmpty()) continue;

            DicomInstance instance = instanceOpt.get();
            String pngPath = instance.getImage() != null ? instance.getImage().getFilePath() : null; // e.g. /images/uuid.png
            if (pngPath == null) continue;

            File imageFile = Paths.get(storageBaseDir, pngPath).toFile();
            if (!imageFile.exists()) {
                log.warn("Image file not found for instance {}: {}", instanceId, imageFile.getAbsolutePath());
                continue;
            }

            try {
                // Call external API
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(imageFile));

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<FastApiPredictionResponse> response = restTemplate.postForEntity(AI_API_URL, requestEntity, FastApiPredictionResponse.class);
                long duration = System.currentTimeMillis() - startTime;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    FastApiPredictionResponse aiData = response.getBody();

                    // Decode base64 GradCAM
                    String gradcamBase64 = aiData.getGradcamImage();
                    String gradcamPath = null;
                    if (gradcamBase64 != null && gradcamBase64.startsWith("data:image")) {
                        String[] parts = gradcamBase64.split(",");
                        if (parts.length == 2) {
                            byte[] decodedImg = Base64.getDecoder().decode(parts[1]);
                            String uniqueName = UUID.randomUUID().toString() + "_gradcam.png";
                            gradcamPath = "/images/" + uniqueName;
                            Path targetPath = Paths.get(storageBaseDir, "images", uniqueName);
                            // Ensure directory exists
                            targetPath.getParent().toFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                                fos.write(decodedImg);
                            }
                        }
                    }

                    // Save AiAnalysis
                    AiAnalysis analysis = new AiAnalysis();
                    analysis.setDicomInstance(instance);
                    analysis.setStartTime(LocalDateTime.now());
                    analysis.setDuration(duration);
                    analysis.setStatus("SUCCESS");
                    analysis = aiAnalysisRepository.save(analysis);

                    // Save AiResult
                    AiResult aiResult = new AiResult();
                    aiResult.setAiAnalysis(analysis);
                    aiResult.setPredictedGrade(aiData.getPredictedClass());
                    aiResult.setConfidence(aiData.getConfidence());
                    aiResult.setDescription(aiData.getDescription());
                    aiResult.setStorageHeatmapFilePath(gradcamPath);
                    aiResult = aiResultRepository.save(aiResult);

                    // Save Confidence Scores
                    if (aiData.getDetails() != null) {
                        AiResultConfidenceScore score = new AiResultConfidenceScore();
                        score.setAiResult(aiResult);
                        score.setC0Confidence(aiData.getDetails().getOrDefault("0Normal", 0.0));
                        score.setC1Confidence(aiData.getDetails().getOrDefault("1Doubtful", 0.0));
                        score.setC2Confidence(aiData.getDetails().getOrDefault("2Mild", 0.0));
                        score.setC3Confidence(aiData.getDetails().getOrDefault("3Moderate", 0.0));
                        score.setC4Confidence(aiData.getDetails().getOrDefault("4Severe", 0.0));
                        aiResultConfidenceScoreRepository.save(score);
                    }

                    // Update Examination Status
                    Examination exam = instance.getExamination();
                    if (exam != null) {
                        exam.setStatus(ExaminationStatus.AI_COMPLETED);
                        examinationRepository.save(exam);
                    }

                    // Build DTO
                    AiPredictionResultDto dto = AiPredictionResultDto.builder()
                            .dicomInstanceId(instanceId)
                            .aiAnalysisId(analysis.getId())
                            .aiResultId(aiResult.getId())
                            .predictedGrade(aiResult.getPredictedGrade())
                            .confidence(aiResult.getConfidence())
                            .description(aiResult.getDescription())
                            .details(aiData.getDetails())
                            .gradcamImageUrl(gradcamPath != null ? "/api/v1/ai/heatmap/" + aiResult.getId() : null)
                            .build();
                            
                    aiResultMap.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(dto);
                    if (exam != null) {
                        uniqueExams.putIfAbsent(exam.getId(), exam);
                        instancesByExam.computeIfAbsent(exam.getId(), k -> new ArrayList<>()).add(instance);
                    }
                } else {
                    log.error("Failed to get prediction from AI. Status: {}", response.getStatusCode());
                }

            } catch (Exception e) {
                log.error("Error during AI prediction for instance {}", instanceId, e);
            }
        }
        
        List<ExaminationDto> finalResults = new ArrayList<>();
        for (Examination exam : uniqueExams.values()) {
            List<DicomInstance> examInstances = instancesByExam.getOrDefault(exam.getId(), new ArrayList<>());
            ExaminationDto examDto = examinationMapper.toDto(exam, examInstances);
            int maxGrade = -1;

            if (examDto != null && examDto.getImages() != null) {
                for (com.g93.be.dto.ExaminationImageDto img : examDto.getImages()) {
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
        
        return finalResults;
    }
}
