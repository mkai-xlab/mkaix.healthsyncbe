package com.g93.be.repository;

import com.g93.be.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceKey(String sourceKey);
    boolean existsBySourceKey(String sourceKey);
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();
}
