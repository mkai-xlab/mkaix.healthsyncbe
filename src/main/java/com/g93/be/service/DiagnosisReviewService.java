package com.g93.be.service;

import com.g93.be.dto.AdjustKlGradeRequest;
import com.g93.be.dto.DiagnosisReviewResponse;

public interface DiagnosisReviewService {
    DiagnosisReviewResponse confirmAiGrade(Long aiResultId, String username);
    DiagnosisReviewResponse adjustKlGrade(Long aiResultId, AdjustKlGradeRequest request, String username);
}
