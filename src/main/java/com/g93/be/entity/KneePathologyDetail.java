package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "knee_pathology_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KneePathologyDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_result_id", nullable = false)
    private AiResult result;

    @Column(name = "c0_normal_ratio")
    private Double c0NormalRatio;

    @Column(name = "c1_normal_ratio")
    private Double c1NormalRatio;

    @Column(name = "c2_normal_ratio")
    private Double c2NormalRatio;

    @Column(name = "c3_normal_ratio")
    private Double c3NormalRatio;

    @Column(name = "c4_normal_ratio")
    private Double c4NormalRatio;

    @Column(name = "c0_normal_compartment")
    private Double c0NormalCompartment;

    @Column(name = "c1_normal_compartment")
    private Double c1NormalCompartment;

    @Column(name = "c2_normal_compartment")
    private Double c2NormalCompartment;

    @Column(name = "c3_normal_compartment")
    private Double c3NormalCompartment;

    @Column(name = "c4_normal_compartment")
    private Double c4NormalCompartment;

    @Column(name = "c0_count")
    private Integer c0Count;

    @Column(name = "c1_count")
    private Integer c1Count;

    @Column(name = "c2_count")
    private Integer c2Count;

    @Column(name = "c3_count")
    private Integer c3Count;

    @Column(name = "c4_count")
    private Integer c4Count;

    @Column(name = "clinical_summary", columnDefinition = "TEXT")
    private String clinicalSummary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
