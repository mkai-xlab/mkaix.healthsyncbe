package com.g93.be.repository;

import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.LocalDate;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExaminationRepository extends JpaRepository<Examination, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Examination e where e.id = :id")
    Optional<Examination> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.doctor.id from Examination e where e.id = :id")
    Optional<Long> findAssignedDoctorIdById(@Param("id") Long id);

    List<Examination> findByPatientId(Long patientId);

    List<Examination> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    boolean existsByPatientIdAndDoctorId(Long patientId, Long doctorId);

    Page<Examination> findByPatientId(Long patientId, Pageable pageable);

    Page<Examination> findByDoctorId(Long doctorId, Pageable pageable);

    long countByDoctorId(Long doctorId);

    long countByMaxPredictedGradeIn(List<Integer> grades);

    long countByDoctorIdAndMaxPredictedGradeIn(Long doctorId, List<Integer> grades);

    long countByStatus(ExaminationStatus status);

    long countByStatusNot(ExaminationStatus status);

    long countByDoctorIdAndStatus(Long doctorId, ExaminationStatus status);

    long countByDoctorIdAndStatusNot(Long doctorId, ExaminationStatus status);

    Optional<Examination> findByEncounterCode(String encounterCode);

    Optional<Examination> findFirstByPatientPatientCodeAndStudyDateOrderByCreatedAtDesc(String patientCode,
            LocalDate studyDate);

    Page<Examination> findByStatus(ExaminationStatus status, Pageable pageable);

    Page<Examination> findByDoctorIdAndStatus(Long doctorId, ExaminationStatus status, Pageable pageable);

    Page<Examination> findByMaxPredictedGrade(Integer grade, Pageable pageable);

    Page<Examination> findByDoctorIdAndMaxPredictedGrade(Long doctorId, Integer grade, Pageable pageable);

    interface GradePatientCountProjection {
        Integer getGrade();

        Long getPatientCount();
    }

    @Query("SELECT e.maxPredictedGrade AS grade, COUNT(e.patient.id) AS patientCount " +
            "FROM Examination e " +
            "WHERE e.createdAt = (" +
            "    SELECT MAX(e2.createdAt) " +
            "    FROM Examination e2 " +
            "    WHERE e2.patient.id = e.patient.id" +
            ") " +
            "GROUP BY e.maxPredictedGrade")
    List<GradePatientCountProjection> countPatientsByLatestGrade();

    @Query("SELECT e.maxPredictedGrade AS grade, COUNT(e.patient.id) AS patientCount " +
            "FROM Examination e " +
            "WHERE e.doctor.id = :doctorId AND e.createdAt = (" +
            "    SELECT MAX(e2.createdAt) " +
            "    FROM Examination e2 " +
            "    WHERE e2.patient.id = e.patient.id AND e2.doctor.id = :doctorId" +
            ") " +
            "GROUP BY e.maxPredictedGrade")
    List<GradePatientCountProjection> countPatientsByLatestGradeForDoctor(
            @Param("doctorId") Long doctorId);

    Page<Examination> findByStudyDate(LocalDate studyDate, Pageable pageable);

    Page<Examination> findByDoctorIdAndStudyDate(Long doctorId, LocalDate studyDate, Pageable pageable);

    Page<Examination> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Examination> findByDoctorIdAndCreatedAtBetween(Long doctorId, LocalDateTime start, LocalDateTime end,
            Pageable pageable);

    Page<Examination> findByPatientIdAndStudyDateBetween(Long patientId, LocalDate startDate, LocalDate endDate,
            Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime date);

    long countByDoctorIdAndCreatedAtAfter(Long doctorId, LocalDateTime date);

    @Query("SELECT e.createdAt FROM Examination e WHERE e.createdAt >= :startDate")
    List<LocalDateTime> findCreatedAtByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT e.createdAt FROM Examination e WHERE e.doctor.id = :doctorId AND e.createdAt >= :startDate")
    List<LocalDateTime> findCreatedAtByDoctorIdAndCreatedAtAfter(@Param("doctorId") Long doctorId,
            @Param("startDate") LocalDateTime startDate);
}
