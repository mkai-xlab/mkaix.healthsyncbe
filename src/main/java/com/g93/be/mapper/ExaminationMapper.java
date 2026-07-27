package com.g93.be.mapper;
import com.g93.be.dto.AiPredictionResultDto;


import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.ExaminationImageDto;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper component for mapping Examination entities to their corresponding DTOs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExaminationMapper {

    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;

    /**
     * Maps an Examination entity to an ExaminationDto.
     * Includes patient mapping and image mapping if dicom instances are provided.
     *
     * @param ex The Examination entity to map.
     * @param instances The list of associated DicomInstance entities.
     * @return The mapped ExaminationDto.
     */
    public ExaminationDto toDto(Examination ex, List<DicomInstance> instances) {
        if (ex == null) {
            return null;
        }

        ExaminationDto ed = new ExaminationDto();
        ed.setExaminationId(ex.getId());
        ed.setEncounterCode(ex.getEncounterCode());
        ed.setStatus(ex.getStatus() != null ? ex.getStatus().name() : null);
        ed.setStudyDate(ex.getStudyDate());
        ed.setVisitTime(ex.getVisitTime());
        ed.setReferringPhysician(ex.getReferringPhysician());
        ed.setStudyTime(ex.getStudyTime());
        ed.setChiefComplaint(ex.getChiefComplaint());
        ed.setClinicalNotes(ex.getClinicalNotes());
        ed.setPriority(ex.getPriority());
        ed.setFinalDiagnosis(ex.getFinalDiagnosis());
        ed.setDescription(ex.getDescription());
        ed.setIsViewed(ex.getIsViewed());
        ed.setMaxPredictedGrade(ex.getMaxPredictedGrade());

        if (ex.getPatient() != null) {
            ed.setPatient(patientMapper.toResponse(ex.getPatient()));
        }

        if (ex.getDoctor() != null) {
            ed.setDoctorId(ex.getDoctor().getId());
        }

        String baseUrl = "/api/v1";
        try {
            if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null) {
                baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            }
        } catch (Exception e) {
            log.warn("Could not determine base URL from request context: {}", e.getMessage());
        }

        if (instances != null && !instances.isEmpty()) {
            ed.setThumbnailUrl(baseUrl + "/dicom/instances/" + instances.get(0).getId() + "/image");
            List<ExaminationImageDto> imageDtos = new ArrayList<>();
            for (DicomInstance instance : instances) {
                ExaminationImageDto img = new ExaminationImageDto();
                img.setDicomInstanceId(instance.getId());
                img.setExaminationId(ex.getId());
                img.setEncounterCode(ex.getEncounterCode());
                img.setStatus(ex.getStatus() != null ? ex.getStatus().name() : null);
                img.setVisitTime(ex.getVisitTime());
                img.setImageUrl(baseUrl + "/dicom/instances/" + instance.getId() + "/image");
                if (instance.getAnnotatedImage() != null) {
                    img.setAnnotatedImageUrl(baseUrl + "/ai/image/" + instance.getAnnotatedImage().getId());
                }

                // Map aiResults lazily
                List<AiPredictionResultDto> aiResList = new ArrayList<>();
                if (instance.getAiAnalyses() != null) {
                    for (AiAnalysis analysis : instance.getAiAnalyses()) {
                        if (analysis.getAiResults() != null) {
                            for (AiResult aiRes : analysis.getAiResults()) {
                                DiagnosisReview review = aiRes.getDiagnosisReview();
                                java.util.Map<String, Double> details = new java.util.HashMap<>();
                                if (aiRes.getConfidenceScore() != null) {
                                    details.put("0Normal", aiRes.getConfidenceScore().getC0Confidence());
                                    details.put("1Doubtful", aiRes.getConfidenceScore().getC1Confidence());
                                    details.put("2Mild", aiRes.getConfidenceScore().getC2Confidence());
                                    details.put("3Moderate", aiRes.getConfidenceScore().getC3Confidence());
                                    details.put("4Severe", aiRes.getConfidenceScore().getC4Confidence());
                                }
                                
                                String gradcamUrl = aiRes.getGradcamImage() != null ? baseUrl + "/ai/image/" + aiRes.getGradcamImage().getId() : 
                                        (aiRes.getStorageHeatmapFilePath() != null ? baseUrl + "/ai/heatmap/" + aiRes.getId() : null);
                                String roiUrl = aiRes.getRoiImage() != null ? baseUrl + "/ai/image/" + aiRes.getRoiImage().getId() : null;
                                String annotatedUrl = instance.getAnnotatedImage() != null ? baseUrl + "/ai/image/" + instance.getAnnotatedImage().getId() : null;

                                AiPredictionResultDto dto = AiPredictionResultDto.builder()
                                    .dicomInstanceId(instance.getId())
                                    .aiAnalysisId(analysis.getId())
                                    .aiResultId(aiRes.getId())
                                    .predictedGrade(aiRes.getPredictedGrade())
                                    .confirmedGrade(review != null ? review.getConfirmedKlGrade() : null)
                                    .effectiveGrade(review != null ? review.getConfirmedKlGrade() : aiRes.getPredictedGrade())
                                    .reviewDecision(review != null ? review.getDecision().name() : null)
                                    .confidence(aiRes.getConfidence())
                                    .description(aiRes.getDescription())
                                    .details(details.isEmpty() ? null : details)
                                    .kneeSide(aiRes.getKneeSide())
                                    .gradcamImageUrl(gradcamUrl)
                                    .roiImageUrl(roiUrl)
                                    .annotatedImageUrl(annotatedUrl)
                                    .reviewNote(review != null ? review.getReviewNote() : null)
                                    .reviewedByDoctorId(review != null ? review.getDoctor().getId() : null)
                                    .reviewedAt(review != null ? review.getReviewedAt() : null)
                                    .build();
                                aiResList.add(dto);
                            }
                            
                            String gradcamUrl = aiRes.getGradcamImage() != null ? baseUrl + "/ai/image/" + aiRes.getGradcamImage().getId() : 
                                    (aiRes.getStorageHeatmapFilePath() != null ? baseUrl + "/ai/heatmap/" + aiRes.getId() : null);
                            String roiUrl = aiRes.getRoiImage() != null ? baseUrl + "/ai/image/" + aiRes.getRoiImage().getId() : null;
                            String annotatedUrl = instance.getAnnotatedImage() != null ? baseUrl + "/ai/image/" + instance.getAnnotatedImage().getId() : null;

                            com.g93.be.dto.AiPredictionResultDto dto = com.g93.be.dto.AiPredictionResultDto.builder()
                                .dicomInstanceId(instance.getId())
                                .aiAnalysisId(analysis.getId())
                                .aiResultId(aiRes.getId())
                                .predictedGrade(aiRes.getPredictedGrade())
                                .confirmedGrade(review != null ? review.getConfirmedKlGrade() : null)
                                .effectiveGrade(review != null ? review.getConfirmedKlGrade() : aiRes.getPredictedGrade())
                                .reviewDecision(review != null ? review.getDecision().name() : null)
                                .confidence(aiRes.getConfidence())
                                .description(aiRes.getDescription())
                                .details(details.isEmpty() ? null : details)
                                .kneeSide(aiRes.getKneeSide())
                                .gradcamImageUrl(gradcamUrl)
                                .roiImageUrl(roiUrl)
                                .annotatedImageUrl(annotatedUrl)
                                .reviewNote(review != null ? review.getReviewNote() : null)
                                .reviewedByDoctorId(review != null ? review.getDoctor().getId() : null)
                                .reviewedAt(review != null ? review.getReviewedAt() : null)
                                .build();
                            aiResList.add(dto);
                        }
                    }
                }
                if (!aiResList.isEmpty()) {
                    img.setAiResults(aiResList);
                }

                imageDtos.add(img);
            }
            ed.setImages(imageDtos);
        }

        return ed;
    }
}

