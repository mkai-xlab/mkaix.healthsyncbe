package com.g93.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_result_id", nullable = false, unique = true)
    private AiResult aiResult;

    @Column(name = "confirmed_kl_grade", nullable = false)
    private Integer confirmedKlGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private DiagnosisReviewDecision decision;

    @Column(name = "review_note", nullable = false, columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
