package com.g93.be.repository;

import com.g93.be.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    Page<ChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);

    /**
     * Unlinks sessions from an examination that is about to be hard-deleted. The
     * database FK for {@code chat_sessions.examination_id} is only guaranteed
     * ON DELETE SET NULL on environments that ran the manual prod migration;
     * environments relying on Hibernate's auto-DDL (local/dev) get a plain
     * RESTRICT constraint, so deleting the examination first would fail with a
     * foreign-key violation. Detaching explicitly works on every environment.
     */
    @Modifying
    @Query("UPDATE ChatSession c SET c.examination = null WHERE c.examination.id = :examinationId")
    int detachExamination(@Param("examinationId") Long examinationId);
}
