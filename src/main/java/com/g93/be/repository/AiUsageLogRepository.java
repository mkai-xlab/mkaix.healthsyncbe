package com.g93.be.repository;

import com.g93.be.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("""
            SELECT l.callType AS callType,
                   COUNT(l) AS calls,
                   COALESCE(SUM(l.promptTokens), 0) AS promptTokens,
                   COALESCE(SUM(l.completionTokens), 0) AS completionTokens,
                   COALESCE(SUM(l.totalTokens), 0) AS totalTokens,
                   COALESCE(SUM(l.costUsd), 0) AS costUsd
            FROM AiUsageLog l
            WHERE l.createdAt >= :from AND l.createdAt < :to
            GROUP BY l.callType
            """)
    List<AiUsageCallTypeTotals> summarizeByCallType(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
