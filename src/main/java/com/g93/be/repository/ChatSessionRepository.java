package com.g93.be.repository;

import com.g93.be.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    Page<ChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);
}
