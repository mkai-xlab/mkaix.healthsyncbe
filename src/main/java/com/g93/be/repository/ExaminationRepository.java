package com.g93.be.repository;

import com.g93.be.entity.Examination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExaminationRepository extends JpaRepository<Examination, Long> {
    List<Examination> findByPatientId(Long patientId);
    Page<Examination> findByPatientId(Long patientId, Pageable pageable);
    Page<Examination> findByDoctorId(Long doctorId, Pageable pageable);
    long countByDoctorId(Long doctorId);
    long countByMaxPredictedGradeIn(List<Integer> grades);
    long countByDoctorIdAndMaxPredictedGradeIn(Long doctorId, List<Integer> grades);
    long countByStatus(com.g93.be.entity.ExaminationStatus status);
    long countByStatusNot(com.g93.be.entity.ExaminationStatus status);
    long countByDoctorIdAndStatus(Long doctorId, com.g93.be.entity.ExaminationStatus status);
    long countByDoctorIdAndStatusNot(Long doctorId, com.g93.be.entity.ExaminationStatus status);
    java.util.Optional<Examination> findByEncounterCode(String encounterCode);
}
