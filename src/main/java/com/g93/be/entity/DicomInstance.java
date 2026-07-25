package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "dicom_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DicomInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_date")
    private LocalDateTime studyDate;

    @Column(name = "study_description", length = 255)
    private String studyDescription;

    @Column(name = "modality", length = 50)
    private String modality;

    @Column(name = "study_instance_uid", length = 150)
    private String studyInstanceUid;

    @Column(name = "series_instance_uid", length = 150)
    private String seriesInstanceUid;

    @Column(name = "sop_instance_uid", length = 255)
    private String sopInstanceUid;

    @Column(name = "seri_id", length = 100)
    private String seriId;

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @Column(name = "image_laterality", length = 10)
    private String imageLaterality;

    @Column(name = "image_rows")
    private Integer imageRows;

    @Column(name = "image_columns")
    private Integer imageColumns;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "dicom_raw_id")
    private DicomRaw dicomRaw;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    private Image image;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "annotated_image_id")
    private Image annotatedImage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DicomInstanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @OneToMany(mappedBy = "dicomInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<AiAnalysis> aiAnalyses;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = DicomInstanceStatus.AI_SENDING;
        }
    }
}
