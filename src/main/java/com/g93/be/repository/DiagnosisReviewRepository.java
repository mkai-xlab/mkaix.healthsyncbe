package com.g93.be.repository;

import com.g93.be.entity.DiagnosisReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiagnosisReviewRepository extends JpaRepository<DiagnosisReview, Long> {
    Optional<DiagnosisReview> findByAiResultId(Long aiResultId);
}
