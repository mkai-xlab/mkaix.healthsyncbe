package com.g93.be.repository;

import com.g93.be.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import com.g93.be.entity.ExaminationStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {
        boolean existsByEmail(String email);

        boolean existsByEmailAndIdNot(String email, Long id);

        Optional<Patient> findByPatientCode(String patientCode);

        @Query("SELECT DISTINCT e.patient FROM Examination e " +
                        "WHERE e.createdAt >= :startOfDay AND e.createdAt < :startOfNextDay " +
                        "AND (:doctorId IS NULL OR e.doctor.id = :doctorId)")
        Page<Patient> findPatientsByUploadDateAndDoctor(
                        @Param("startOfDay") LocalDateTime startOfDay,
                        @Param("startOfNextDay") LocalDateTime startOfNextDay,
                        @Param("doctorId") Long doctorId,
                        Pageable pageable);

        @Query(value = "SELECT p FROM Patient p " +
                        "LEFT JOIN Examination e1 ON e1.patient = p AND e1.id = (SELECT MAX(e2.id) FROM Examination e2 WHERE e2.patient = p) "
                        +
                        "WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
                        +
                        "AND (:hasStatuses = false OR (e1 IS NOT NULL AND e1.status IN :statuses)) " +
                        "AND (:hasSeverities = false OR (e1 IS NOT NULL AND e1.maxPredictedGrade IN :severities)) " +
                        "AND (:doctorId IS NULL OR EXISTS (SELECT 1 FROM Examination e3 WHERE e3.patient = p AND e3.doctor.id = :doctorId)) "
                        +
                        "ORDER BY COALESCE(e1.maxPredictedGrade, -1) DESC, COALESCE(e1.createdAt, p.updatedAt, p.createdAt) DESC", countQuery = "SELECT COUNT(p) FROM Patient p "
                                        +
                                        "LEFT JOIN Examination e1 ON e1.patient = p AND e1.id = (SELECT MAX(e2.id) FROM Examination e2 WHERE e2.patient = p) "
                                        +
                                        "WHERE (:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
                                        +
                                        "AND (:hasStatuses = false OR (e1 IS NOT NULL AND e1.status IN :statuses)) " +
                                        "AND (:hasSeverities = false OR (e1 IS NOT NULL AND e1.maxPredictedGrade IN :severities)) "
                                        +
                                        "AND (:doctorId IS NULL OR EXISTS (SELECT 1 FROM Examination e3 WHERE e3.patient = p AND e3.doctor.id = :doctorId))")
        Page<Patient> findAllByCustomFilters(
                        @Param("keyword") String keyword,
                        @Param("hasStatuses") boolean hasStatuses,
                        @Param("statuses") List<ExaminationStatus> statuses,
                        @Param("hasSeverities") boolean hasSeverities,
                        @Param("severities") List<Integer> severities,
                        @Param("doctorId") Long doctorId,
                        Pageable pageable);
}
