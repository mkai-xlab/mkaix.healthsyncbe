package com.g93.be.service.impl;

import com.g93.be.aspect.LogAction;
import com.g93.be.dto.AdjustKlGradeRequest;
import com.g93.be.dto.DiagnosisReviewResponse;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DiagnosisReviewDecision;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.DiagnosisReviewRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.service.DiagnosisReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiagnosisReviewServiceImpl implements DiagnosisReviewService {

    private final AiResultRepository aiResultRepository;
    private final DiagnosisReviewRepository diagnosisReviewRepository;
    private final DoctorRepository doctorRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ExaminationRepository examinationRepository;

    @Override
    @Transactional
    @LogAction("CONFIRM_AI_GRADE")
    public DiagnosisReviewResponse confirmAiGrade(Long aiResultId, String username) {
        ReviewContext context = loadReviewContext(aiResultId, username, true);
        validateGrade(context.aiResult().getPredictedGrade());
        return saveReview(
                context,
                context.aiResult().getPredictedGrade(),
                DiagnosisReviewDecision.AI_CONFIRMED,
                "AI result confirmed");
    }

    @Override
    @Transactional
    @LogAction("OVERRIDE_AI_GRADE")
    public DiagnosisReviewResponse adjustKlGrade(
            Long aiResultId,
            AdjustKlGradeRequest request,
            String username) {
        if (request == null || request.reviewNote() == null || request.reviewNote().isBlank()) {
            throw new IllegalArgumentException("Review note is required");
        }
        validateGrade(request.confirmedKlGrade());

        ReviewContext context = loadReviewContext(aiResultId, username, true);
        return saveReview(
                context,
                request.confirmedKlGrade(),
                DiagnosisReviewDecision.DOCTOR_ADJUSTED,
                request.reviewNote().trim());
    }

    private DiagnosisReviewResponse saveReview(
            ReviewContext context,
            Integer confirmedGrade,
            DiagnosisReviewDecision decision,
            String reviewNote) {
        DiagnosisReview review = diagnosisReviewRepository.findByAiResultId(context.aiResult().getId())
                .orElseGet(DiagnosisReview::new);
        review.setAiResult(context.aiResult());
        review.setExamination(context.examination());
        review.setDoctor(context.reviewer());
        review.setConfirmedKlGrade(confirmedGrade);
        review.setDecision(decision);
        review.setReviewNote(reviewNote);
        review.setReviewedAt(LocalDateTime.now());

        DiagnosisReview savedReview = diagnosisReviewRepository.save(review);
        context.aiResult().setDiagnosisReview(savedReview);
        markVerifiedWhenAllLatestResultsAreReviewed(context.examination());
        return toResponse(savedReview);
    }

    private ReviewContext loadReviewContext(
            Long aiResultId,
            String username,
            boolean departmentHeadCanReviewUnassignedExamination) {
        AiResult aiResult = aiResultRepository.findById(aiResultId)
                .orElseThrow(() -> new IllegalArgumentException("AI result not found with ID: " + aiResultId));
        Doctor reviewer = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found: " + username));
        Examination examination = getExamination(aiResult);
        if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
            throw new IllegalArgumentException("Cannot review an examination after its report has been generated");
        }

        if (!(departmentHeadCanReviewUnassignedExamination && isDepartmentHead(reviewer))
                && (examination.getDoctor() == null
                || !Objects.equals(examination.getDoctor().getId(), reviewer.getId()))) {
            throw new AccessDeniedException("Doctor is not assigned to this examination");
        }
        return new ReviewContext(aiResult, examination, reviewer);
    }

    private void markVerifiedWhenAllLatestResultsAreReviewed(Examination examination) {
        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
        if (instances.isEmpty()) {
            return;
        }

        for (DicomInstance instance : instances) {
            AiAnalysis latestAnalysis = latestAnalysis(instance);
            if (latestAnalysis == null
                    || latestAnalysis.getAiResults() == null
                    || latestAnalysis.getAiResults().isEmpty()) {
                return;
            }
            if (latestAnalysis.getAiResults().stream()
                    .anyMatch(result -> result.getDiagnosisReview() == null)) {
                return;
            }
        }

        examination.setStatus(ExaminationStatus.VERIFIED);
        examinationRepository.save(examination);
    }

    private AiAnalysis latestAnalysis(DicomInstance instance) {
        if (instance.getAiAnalyses() == null) {
            return null;
        }
        return instance.getAiAnalyses().stream()
                .max(Comparator
                        .comparing(AiAnalysis::getStartTime,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AiAnalysis::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isDepartmentHead(Doctor reviewer) {
        if (reviewer.getRole() == null || reviewer.getRole().getCode() == null) {
            return false;
        }
        String roleCode = reviewer.getRole().getCode();
        return "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)
                || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode);
    }

    private void validateGrade(Integer grade) {
        if (grade == null || grade < 0 || grade > 4) {
            throw new IllegalArgumentException("Confirmed KL grade must be between 0 and 4");
        }
    }

    private Examination getExamination(AiResult aiResult) {
        if (aiResult.getAiAnalysis() == null
                || aiResult.getAiAnalysis().getDicomInstance() == null
                || aiResult.getAiAnalysis().getDicomInstance().getExamination() == null) {
            throw new IllegalStateException("AI result is not linked to an examination");
        }
        return aiResult.getAiAnalysis().getDicomInstance().getExamination();
    }

    private DiagnosisReviewResponse toResponse(DiagnosisReview review) {
        return new DiagnosisReviewResponse(
                review.getId(),
                review.getAiResult().getId(),
                review.getExamination().getId(),
                review.getAiResult().getPredictedGrade(),
                review.getConfirmedKlGrade(),
                review.getDecision().name(),
                review.getReviewNote(),
                review.getDoctor().getId(),
                review.getReviewedAt());
    }

    private record ReviewContext(AiResult aiResult, Examination examination, Doctor reviewer) {
    }
}
