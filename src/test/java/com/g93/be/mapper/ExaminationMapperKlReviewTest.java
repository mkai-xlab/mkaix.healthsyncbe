package com.g93.be.mapper;

import com.g93.be.dto.AiPredictionResultDto;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.AiAnalysis;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.DiagnosisReview;
import com.g93.be.entity.DiagnosisReviewDecision;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Image;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExaminationMapperKlReviewTest {

    private final ExaminationMapper mapper = new ExaminationMapper(
            mock(PatientMapper.class), mock(DoctorMapper.class));

    @Test
    void mapsPredictedConfirmedAndEffectiveKlGrades() {
        Doctor doctor = new Doctor();
        doctor.setId(7L);
        Examination examination = new Examination();
        examination.setId(11L);

        DicomInstance instance = new DicomInstance();
        instance.setId(13L);
        instance.setExamination(examination);
        Image annotatedImage = new Image();
        annotatedImage.setId(31L);
        instance.setAnnotatedImage(annotatedImage);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setId(17L);
        analysis.setDicomInstance(instance);

        AiResult aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(2);
        aiResult.setKneeSide("LEFT");
        aiResult.setAiAnalysis(analysis);
        Image roiImage = new Image();
        roiImage.setId(29L);
        aiResult.setRoiImage(roiImage);
        Image gradcamImage = new Image();
        gradcamImage.setId(30L);
        aiResult.setGradcamImage(gradcamImage);

        DiagnosisReview review = new DiagnosisReview();
        review.setConfirmedKlGrade(3);
        review.setDecision(DiagnosisReviewDecision.DOCTOR_ADJUSTED);
        review.setReviewNote("Clinical signs support KL3");
        review.setReviewedAt(LocalDateTime.of(2026, 7, 25, 9, 30));
        review.setDoctor(doctor);
        review.setAiResult(aiResult);
        aiResult.setDiagnosisReview(review);
        analysis.setAiResults(List.of(aiResult));
        instance.setAiAnalysis(analysis);

        ExaminationDto result = mapper.toDto(examination, List.of(instance));
        AiPredictionResultDto mappedResult = result.getImages().getFirst().getAiResults().getFirst();

        assertEquals(2, mappedResult.getPredictedGrade());
        assertEquals(3, mappedResult.getConfirmedGrade());
        assertEquals(3, mappedResult.getEffectiveGrade());
        assertEquals("DOCTOR_ADJUSTED", mappedResult.getReviewDecision());
        assertEquals("Clinical signs support KL3", mappedResult.getReviewNote());
        assertEquals(7L, mappedResult.getReviewedByDoctorId());
        assertEquals("LEFT", mappedResult.getKneeSide());
        assertEquals("/api/v1/ai/image/29", mappedResult.getRoiImageUrl());
        assertEquals("/api/v1/ai/image/30", mappedResult.getGradcamImageUrl());
        assertEquals("/api/v1/ai/image/31", mappedResult.getAnnotatedImageUrl());
        assertEquals("/api/v1/ai/image/31", result.getImages().getFirst().getAnnotatedImageUrl());
    }
}
