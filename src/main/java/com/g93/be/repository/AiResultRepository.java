package com.g93.be.repository;

import com.g93.be.entity.AiResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiResultRepository extends JpaRepository<AiResult, Long> {
}
