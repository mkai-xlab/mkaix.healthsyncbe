package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationImageDto {
    private Long examinationId;
    private String encounterCode;
    private String status;
    private LocalDateTime visitTime;
    private String imageUrl;
}
