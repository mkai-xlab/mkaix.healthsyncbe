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

    @Column(name = "storage_url", length = 512)
    private String storageUrl;

    @Column(name = "storage_heatmap_file_path", length = 512)
    private String storageHeatmapFilePath;

    @Column(name = "confidence")
    private Double confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_analysis_id", nullable = false)
    private AiAnalysis aiAnalysis;
}
