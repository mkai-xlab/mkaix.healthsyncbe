package com.g93.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class FastApiPredictionResponse {
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

    @JsonProperty("gradcam_image")
    private String gradcamImage;

    @JsonProperty("filename")
    private String filename;
}
