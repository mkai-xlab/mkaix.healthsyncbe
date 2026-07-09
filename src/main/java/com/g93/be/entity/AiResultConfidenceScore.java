package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_result_confidence_score")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiResultConfidenceScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_result_id", nullable = false)
    private AiResult aiResult;

    @Column(name = "c0_confidence")
    private Double c0Confidence;

    @Column(name = "c1_confidence")
    private Double c1Confidence;

    @Column(name = "c2_confidence")
    private Double c2Confidence;

    @Column(name = "c3_confidence")
    private Double c3Confidence;

    @Column(name = "c4_confidence")
    private Double c4Confidence;
}
