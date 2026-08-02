package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "duration")
    private Long duration;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "token", length = 255)
    private String token;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dicom_instance_id", nullable = false)
    private DicomInstance dicomInstance;

    @OneToMany(mappedBy = "aiAnalysis", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<AiResult> aiResults;
}
