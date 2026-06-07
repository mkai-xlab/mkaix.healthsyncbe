package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing the AI analysis result for a single X-ray image.
 */
@Entity
@Table(name = "ai_analysis_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private AiAnalysisJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xray_image_id", nullable = false)
    private XrayImage xrayImage;

    @Column(name = "predicted_kl_grade", nullable = false)
    private Integer predictedKlGrade;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "gradcam_url", length = 500)
    private String gradcamUrl;

    @Column(name = "interpretation", columnDefinition = "TEXT")
    private String interpretation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
