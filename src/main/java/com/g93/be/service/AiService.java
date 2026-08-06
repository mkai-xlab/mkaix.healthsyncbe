package com.g93.be.service;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;

import java.util.List;

public interface AiService {
    List<ExaminationDto> predictBatch(AiPredictionRequest request);
    org.springframework.core.io.Resource getHeatmapImageResource(Long aiResultId);
}
