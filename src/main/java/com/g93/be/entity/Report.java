package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operating_doctor_id")
    private User operatingDoctor;

    @Column(name = "study_uid", length = 255)
    private String studyUid;

    @Column(name = "study_id", length = 255)
    private String studyId;

    @Column(name = "doctor_recording_time_in_s")
    private Integer doctorRecordingTimeInS;

    @Column(name = "ai_processing_time_in_s")
    private Integer aiProcessingTimeInS;

    @Column(name = "clinical_summary", columnDefinition = "TEXT")
    private String clinicalSummary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
