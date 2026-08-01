package com.g93.be.repository;

import com.g93.be.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import com.g93.be.entity.ExaminationStatus;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {
    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    java.util.Optional<Patient> findByPatientCode(String patientCode);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.patient FROM Examination e " +
            "WHERE e.createdAt >= :startOfDay AND e.createdAt < :startOfNextDay " +
            "AND (:doctorId IS NULL OR e.doctor.id = :doctorId)")
    org.springframework.data.domain.Page<Patient> findPatientsByUploadDateAndDoctor(
            @org.springframework.data.repository.query.Param("startOfDay") java.time.LocalDateTime startOfDay,
            @org.springframework.data.repository.query.Param("startOfNextDay") java.time.LocalDateTime startOfNextDay,
            @org.springframework.data.repository.query.Param("doctorId") Long doctorId,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT p FROM Patient p " +
                "LEFT JOIN Examination e1 ON e1.patient = p AND e1.id = (SELECT MAX(e2.id) FROM Examination e2 WHERE e2.patient = p) " +
                "WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                "AND (:hasStatuses = false OR (e1 IS NOT NULL AND e1.status IN :statuses)) " +
                "AND (:hasSeverities = false OR (e1 IS NOT NULL AND e1.maxPredictedGrade IN :severities)) " +
                "AND (:doctorId IS NULL OR EXISTS (SELECT 1 FROM Examination e3 WHERE e3.patient = p AND e3.doctor.id = :doctorId)) " +
                "ORDER BY COALESCE(e1.maxPredictedGrade, -1) DESC, COALESCE(e1.createdAt, p.updatedAt, p.createdAt) DESC",
        countQuery = "SELECT COUNT(p) FROM Patient p " +
                "LEFT JOIN Examination e1 ON e1.patient = p AND e1.id = (SELECT MAX(e2.id) FROM Examination e2 WHERE e2.patient = p) " +
                "WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                "AND (:hasStatuses = false OR (e1 IS NOT NULL AND e1.status IN :statuses)) " +
                "AND (:hasSeverities = false OR (e1 IS NOT NULL AND e1.maxPredictedGrade IN :severities)) " +
                "AND (:doctorId IS NULL OR EXISTS (SELECT 1 FROM Examination e3 WHERE e3.patient = p AND e3.doctor.id = :doctorId))"
    )
    org.springframework.data.domain.Page<Patient> findAllByCustomFilters(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("hasStatuses") boolean hasStatuses,
            @org.springframework.data.repository.query.Param("statuses") java.util.List<ExaminationStatus> statuses,
            @org.springframework.data.repository.query.Param("hasSeverities") boolean hasSeverities,
            @org.springframework.data.repository.query.Param("severities") java.util.List<Integer> severities,
            @org.springframework.data.repository.query.Param("doctorId") Long doctorId,
            org.springframework.data.domain.Pageable pageable);
}
