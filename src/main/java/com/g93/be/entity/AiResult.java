package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "predicted_grade")
    private Integer predictedGrade;

    @Column(name = "knee_side", length = 10)
    private String kneeSide;

    @Column(name = "storage_url", length = 512)
    private String storageUrl;

    @Column(name = "storage_heatmap_file_path", length = 512)
    private String storageHeatmapFilePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roi_image_id")
    private Image roiImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gradcam_image_id")
    private Image gradcamImage;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_analysis_id", nullable = false)
    private AiAnalysis aiAnalysis;

    @OneToOne(mappedBy = "aiResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AiResultConfidenceScore confidenceScore;

    @OneToOne(mappedBy = "aiResult", fetch = FetchType.LAZY)
    private DiagnosisReview diagnosisReview;
}
