package com.g93.be.repository;

import com.g93.be.entity.Examination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface ExaminationRepository extends JpaRepository<Examination, Long>, JpaSpecificationExecutor<Examination> {
    List<Examination> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
