package com.g93.be.service;

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
import com.g93.be.entity.Role;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.DiagnosisReviewRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.service.impl.DiagnosisReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisReviewServiceTest {

    @Mock
    private AiResultRepository aiResultRepository;
    @Mock
    private DiagnosisReviewRepository diagnosisReviewRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private ExaminationRepository examinationRepository;

    @InjectMocks
    private DiagnosisReviewServiceImpl diagnosisReviewService;

    private AiResult aiResult;
    private Examination examination;
    private Doctor assignedDoctor;
    private DicomInstance instance;
    private AiAnalysis analysis;

    @BeforeEach
    void setUp() {
        assignedDoctor = new Doctor();
        assignedDoctor.setId(7L);
        assignedDoctor.setUsername("doctor.b");

        examination = new Examination();
        examination.setId(11L);
        examination.setDoctor(assignedDoctor);

        instance = new DicomInstance();
        instance.setId(13L);
        instance.setExamination(examination);

        analysis = new AiAnalysis();
        analysis.setId(17L);
        analysis.setStartTime(LocalDateTime.now());
        analysis.setDicomInstance(instance);

        aiResult = new AiResult();
        aiResult.setId(19L);
        aiResult.setPredictedGrade(2);
        aiResult.setAiAnalysis(analysis);
        analysis.setAiResults(List.of(aiResult));
        instance.setAiAnalyses(List.of(analysis));
    }

    @Test
    void adjustKlGradeCreatesReviewAndPreservesAiPrediction() {
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.b")).thenReturn(Optional.of(assignedDoctor));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.empty());
        when(diagnosisReviewRepository.save(any(DiagnosisReview.class))).thenAnswer(invocation -> {
            DiagnosisReview review = invocation.getArgument(0);
            review.setId(23L);
            return review;
        });

        DiagnosisReviewResponse response = diagnosisReviewService.adjustKlGrade(
                19L, new AdjustKlGradeRequest(3, "  Joint-space narrowing is more advanced  "), "doctor.b");

        assertEquals(2, aiResult.getPredictedGrade());
        assertEquals(2, response.predictedKlGrade());
        assertEquals(3, response.confirmedKlGrade());
        assertEquals("DOCTOR_ADJUSTED", response.decision());
        assertEquals("Joint-space narrowing is more advanced", response.reviewNote());
        assertEquals(7L, response.reviewedByDoctorId());
        assertNotNull(response.reviewedAt());
        assertSame(aiResult.getDiagnosisReview().getAiResult(), aiResult);
    }

    @Test
    void adjustKlGradeUpdatesExistingReview() {
        DiagnosisReview existingReview = new DiagnosisReview();
        existingReview.setId(23L);
        existingReview.setAiResult(aiResult);
        existingReview.setExamination(examination);
        existingReview.setDoctor(assignedDoctor);
        existingReview.setConfirmedKlGrade(3);
        existingReview.setDecision(DiagnosisReviewDecision.DOCTOR_ADJUSTED);
        existingReview.setReviewNote("Initial review");
        existingReview.setReviewedAt(LocalDateTime.now().minusDays(1));
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.b")).thenReturn(Optional.of(assignedDoctor));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.of(existingReview));
        when(diagnosisReviewRepository.save(existingReview)).thenReturn(existingReview);

        DiagnosisReviewResponse response = diagnosisReviewService.adjustKlGrade(
                19L, new AdjustKlGradeRequest(4, "Severe osteophytes"), "doctor.b");

        assertEquals(23L, response.reviewId());
        assertEquals(4, existingReview.getConfirmedKlGrade());
        assertEquals("Severe osteophytes", existingReview.getReviewNote());
        verify(diagnosisReviewRepository).save(existingReview);
    }

    @Test
    void confirmAiGradeUsesOriginalPredictionAsFinalGrade() {
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.b")).thenReturn(Optional.of(assignedDoctor));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.empty());
        when(diagnosisReviewRepository.save(any(DiagnosisReview.class))).thenAnswer(invocation -> {
            DiagnosisReview review = invocation.getArgument(0);
            review.setId(23L);
            return review;
        });

        DiagnosisReviewResponse response = diagnosisReviewService.confirmAiGrade(19L, "doctor.b");

        assertEquals(2, response.predictedKlGrade());
        assertEquals(2, response.confirmedKlGrade());
        assertEquals("AI_CONFIRMED", response.decision());
        assertEquals("AI result confirmed", response.reviewNote());
    }

    @Test
    void finalReviewMarksExaminationAsVerified() {
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.b")).thenReturn(Optional.of(assignedDoctor));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.empty());
        when(diagnosisReviewRepository.save(any(DiagnosisReview.class))).thenAnswer(invocation -> {
            DiagnosisReview review = invocation.getArgument(0);
            review.setId(23L);
            return review;
        });
        when(dicomInstanceRepository.findByExaminationId(11L)).thenReturn(List.of(instance));

        diagnosisReviewService.confirmAiGrade(19L, "doctor.b");

        assertEquals(ExaminationStatus.VERIFIED, examination.getStatus());
        verify(examinationRepository).save(examination);
    }

    @Test
    void reviewIsRejectedAfterReportWasGenerated() {
        examination.setStatus(ExaminationStatus.REPORT_GENERATED);
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.b")).thenReturn(Optional.of(assignedDoctor));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> diagnosisReviewService.confirmAiGrade(19L, "doctor.b"));

        assertEquals("Cannot review an examination after its report has been generated", error.getMessage());
        verify(diagnosisReviewRepository, never()).save(any());
    }

    @Test
    void departmentHeadCanAdjustExaminationAssignedToAnotherDoctor() {
        Doctor departmentHead = new Doctor();
        departmentHead.setId(8L);
        departmentHead.setUsername("department.head");
        Role role = new Role();
        role.setCode("DEPARTMENT_HEAD");
        departmentHead.setRole(role);
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("department.head")).thenReturn(Optional.of(departmentHead));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.empty());
        when(diagnosisReviewRepository.save(any(DiagnosisReview.class))).thenAnswer(invocation -> {
            DiagnosisReview review = invocation.getArgument(0);
            review.setId(23L);
            return review;
        });

        DiagnosisReviewResponse response = diagnosisReviewService.adjustKlGrade(
                19L, new AdjustKlGradeRequest(4, "Department head review"), "department.head");

        assertEquals(4, response.confirmedKlGrade());
        assertEquals(8L, response.reviewedByDoctorId());
        assertEquals("DOCTOR_ADJUSTED", response.decision());
    }

    @Test
    void departmentHeadCanConfirmExaminationAssignedToAnotherDoctor() {
        Doctor departmentHead = new Doctor();
        departmentHead.setId(8L);
        departmentHead.setUsername("department.head");
        Role role = new Role();
        role.setCode("DEPARTMENT_HEAD");
        departmentHead.setRole(role);
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("department.head")).thenReturn(Optional.of(departmentHead));
        when(diagnosisReviewRepository.findByAiResultId(19L)).thenReturn(Optional.empty());
        when(diagnosisReviewRepository.save(any(DiagnosisReview.class))).thenAnswer(invocation -> {
            DiagnosisReview review = invocation.getArgument(0);
            review.setId(23L);
            return review;
        });

        DiagnosisReviewResponse response = diagnosisReviewService.confirmAiGrade(19L, "department.head");

        assertEquals(2, response.confirmedKlGrade());
        assertEquals(8L, response.reviewedByDoctorId());
        assertEquals("AI_CONFIRMED", response.decision());
    }

    @Test
    void confirmAndAdjustActionsAreAuditLogged() throws NoSuchMethodException {
        LogAction confirmAction = DiagnosisReviewServiceImpl.class
                .getMethod("confirmAiGrade", Long.class, String.class)
                .getAnnotation(LogAction.class);
        LogAction adjustAction = DiagnosisReviewServiceImpl.class
                .getMethod("adjustKlGrade", Long.class, AdjustKlGradeRequest.class, String.class)
                .getAnnotation(LogAction.class);

        assertNotNull(confirmAction);
        assertEquals("CONFIRM_AI_GRADE", confirmAction.value());
        assertNotNull(adjustAction);
        assertEquals("OVERRIDE_AI_GRADE", adjustAction.value());
    }

    @Test
    void adjustKlGradeRejectsGradeOutsideKlScale() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> diagnosisReviewService.adjustKlGrade(
                        19L, new AdjustKlGradeRequest(5, "Invalid grade"), "doctor.b"));

        assertEquals("Confirmed KL grade must be between 0 and 4", error.getMessage());
        verify(aiResultRepository, never()).findById(any());
    }

    @Test
    void adjustKlGradeRejectsUnknownAiResult() {
        when(aiResultRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> diagnosisReviewService.adjustKlGrade(
                        999L, new AdjustKlGradeRequest(3, "Review"), "doctor.b"));

        assertEquals("AI result not found with ID: 999", error.getMessage());
        verify(diagnosisReviewRepository, never()).save(any());
    }

    @Test
    void adjustKlGradeRejectsUnknownDoctor() {
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> diagnosisReviewService.adjustKlGrade(
                        19L, new AdjustKlGradeRequest(3, "Review"), "unknown"));

        assertEquals("Doctor not found: unknown", error.getMessage());
        verify(diagnosisReviewRepository, never()).save(any());
    }

    @Test
    void adjustKlGradeRejectsDoctorNotAssignedToExamination() {
        Doctor otherDoctor = new Doctor();
        otherDoctor.setId(8L);
        otherDoctor.setUsername("doctor.other");
        when(aiResultRepository.findById(19L)).thenReturn(Optional.of(aiResult));
        when(doctorRepository.findByUsername("doctor.other")).thenReturn(Optional.of(otherDoctor));

        AccessDeniedException error = assertThrows(AccessDeniedException.class,
                () -> diagnosisReviewService.adjustKlGrade(
                        19L, new AdjustKlGradeRequest(3, "Review"), "doctor.other"));

        assertEquals("Doctor is not assigned to this examination", error.getMessage());
        verify(diagnosisReviewRepository, never()).save(any());
    }
}
