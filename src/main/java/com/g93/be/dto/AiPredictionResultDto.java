package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPredictionResultDto {
    private Long dicomInstanceId;
    private Long aiAnalysisId;
    private Long aiResultId;
    private Integer predictedGrade;
    private Integer confirmedGrade;
    private Integer effectiveGrade;
    private String reviewDecision;
    private Double confidence;
    private String description;
    private Map<String, Double> details;
    private String kneeSide;
    private String roiImageUrl;
    private String gradcamImageUrl;
    private String annotatedImageUrl;
    private String reviewNote;
    private Long reviewedByDoctorId;
    private LocalDateTime reviewedAt;
}
