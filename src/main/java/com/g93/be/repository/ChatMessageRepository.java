package com.g93.be.repository;

import com.g93.be.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId, Pageable pageable);

    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}
