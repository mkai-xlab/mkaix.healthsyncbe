package com.g93.be.repository;

import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.entity.KnowledgeSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceKey(String sourceKey);
    boolean existsBySourceKey(String sourceKey);
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT document
            FROM KnowledgeDocument document
            WHERE (:keyword IS NULL
                    OR LOWER(document.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(document.originalName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:sourceType IS NULL OR document.sourceType = :sourceType)
              AND (:status IS NULL OR document.status = :status)
              AND (:accessScope IS NULL OR document.accessScope = :accessScope)
            """)
    Page<KnowledgeDocument> search(
            @Param("keyword") String keyword,
            @Param("sourceType") KnowledgeSourceType sourceType,
            @Param("status") KnowledgeDocumentStatus status,
            @Param("accessScope") KnowledgeAccessScope accessScope,
            Pageable pageable);
}
