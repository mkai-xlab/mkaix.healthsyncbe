package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "examinations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Examination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", referencedColumnName = "patient_code", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true)
    private Doctor doctor;

    @Column(name = "encounter_code", length = 100)
    private String encounterCode;

    @Column(name = "visit_time")
    private LocalDateTime visitTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExaminationStatus status = ExaminationStatus.CREATED;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "priority", length = 50)
    private String priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "final_diagnosis", columnDefinition = "TEXT")
    private String finalDiagnosis;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "study_date")
    private java.time.LocalDate studyDate;

    @Column(name = "study_time")
    private java.time.LocalTime studyTime;

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "referring_physician", length = 255)
    private String referringPhysician;

    @Column(name = "is_viewed", columnDefinition = "TINYINT DEFAULT 0")
    private Integer isViewed = 0;

    @Column(name = "max_predicted_grade")
    private Integer maxPredictedGrade;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ExaminationStatus.CREATED;
        }
    }
}
