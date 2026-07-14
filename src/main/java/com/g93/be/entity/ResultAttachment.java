package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "result_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dicom_study_id", nullable = false)
    private DicomInstance dicomInstance;

    @Column(name = "KL_level", length = 50)
    private String klLevel;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "accuracy_rate")
    private Double accuracyRate;
}
