package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Entity representing a doctor's review of AI analysis results.
 */
@Entity
@Table(name = "diagnosis_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_result_id")
    private AiAnalysisResult aiResult;

    @Column(name = "confirmed_kl_grade")
    private Integer confirmedKlGrade;

    @Column(name = "doctor_diagnosis", columnDefinition = "TEXT")
    private String doctorDiagnosis;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        reviewedAt = LocalDateTime.now();
    }
}
