package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for representing an image associated with an Examination.
 * Contains details such as the image URL and the context of the examination it belongs to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationImageDto {
    private Long dicomInstanceId;
    private Long examinationId;
    private String encounterCode;
    private String status;
    private LocalDateTime visitTime;
    private String imageUrl;
    private AiPredictionResultDto aiResult;
}
