package com.g93.be.service;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import org.springframework.core.io.Resource;

import java.util.List;

public interface AiService {
    List<ExaminationDto> predictBatch(AiPredictionRequest request);

    Resource getHeatmapImageResource(Long aiResultId);
}
