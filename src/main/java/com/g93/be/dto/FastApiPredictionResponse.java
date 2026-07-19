package com.g93.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class FastApiPredictionResponse {
    @JsonProperty("filename")
    private String filename;

    @JsonProperty("predictions")
    private java.util.List<AiPredictionData> predictions;

    @JsonProperty("annotated_image")
    private String annotatedImage;

    @Data
    @NoArgsConstructor
    public static class AiPredictionData {
        @JsonProperty("predicted_class")
        private Integer predictedClass;

        @JsonProperty("predicted_grade")
        private String predictedGrade;

        @JsonProperty("confidence")
        private Double confidence;

        @JsonProperty("description")
        private String description;

        @JsonProperty("details")
        private Map<String, Double> details;
        
        @JsonProperty("box")
        private java.util.List<Integer> box;
        
        @JsonProperty("yolo_confidence")
        private Double yoloConfidence;
        
        @JsonProperty("knee_side")
        private String kneeSide;
        
        @JsonProperty("roi_image")
        private String roiImage;

        @JsonProperty("gradcam_image")
        private String gradcamImage;
    }
}
