package com.g93.be.repository;


import com.g93.be.entity.AiResultConfidenceScore;
import com.g93.be.entity.AiResultConfidenceScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiResultConfidenceScoreRepository extends JpaRepository<AiResultConfidenceScore, Long> {
}
