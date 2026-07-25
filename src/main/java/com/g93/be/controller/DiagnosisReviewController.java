package com.g93.be.controller;

import com.g93.be.dto.AdjustKlGradeRequest;
import com.g93.be.dto.DiagnosisReviewResponse;
import com.g93.be.service.DiagnosisReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/ai/results")
@RequiredArgsConstructor
public class DiagnosisReviewController {

    private final DiagnosisReviewService diagnosisReviewService;

    /**
     * Confirms that the final KL grade is the grade predicted by AI.
     */
    @PutMapping("/{aiResultId}/confirm")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('CONFIRM_CONCLUSION'))")
    public ResponseEntity<DiagnosisReviewResponse> confirmAiGrade(
            @PathVariable Long aiResultId,
            Principal principal) {
        return ResponseEntity.ok(diagnosisReviewService.confirmAiGrade(aiResultId, principal.getName()));
    }

    /**
     * Overrides an AI-predicted KL grade for an assigned doctor or a department head.
     */
    @PutMapping("/{aiResultId}/kl-grade")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('OVERRIDE_AI_GRADE'))")
    public ResponseEntity<DiagnosisReviewResponse> adjustKlGrade(
            @PathVariable Long aiResultId,
            @Valid @RequestBody AdjustKlGradeRequest request,
            Principal principal) {
        return ResponseEntity.ok(
                diagnosisReviewService.adjustKlGrade(aiResultId, request, principal.getName()));
    }
}
