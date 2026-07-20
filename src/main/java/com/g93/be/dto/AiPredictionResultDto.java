package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Double confidence;
    private String description;
    private Map<String, Double> details;
    private String kneeSide;
    private String roiImageUrl;
    private String gradcamImageUrl;
    private String annotatedImageUrl;
}
