package com.g93.be.repository;


import com.g93.be.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Optional<Report> findFirstByExaminationIdOrderByCreatedAtDesc(Long examinationId);

    @EntityGraph(attributePaths = {"examination", "examination.patient", "examination.doctor"})
    Page<Report> findByExamination_Doctor_Id(Long doctorId, Pageable pageable);

    @EntityGraph(attributePaths = {"examination", "examination.patient", "examination.doctor"})
    Page<Report> findAll(Pageable pageable);
}
