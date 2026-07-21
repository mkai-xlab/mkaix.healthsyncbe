package com.g93.be.repository;


import com.g93.be.entity.Examination;
import com.g93.be.entity.ExaminationStatus;
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
    long countByStatus(ExaminationStatus status);
    long countByStatusNot(ExaminationStatus status);
    long countByDoctorIdAndStatus(Long doctorId, ExaminationStatus status);
    long countByDoctorIdAndStatusNot(Long doctorId, ExaminationStatus status);
    java.util.Optional<Examination> findByEncounterCode(String encounterCode);
    java.util.Optional<Examination> findFirstByPatientPatientCodeAndStudyDateOrderByCreatedAtDesc(String patientCode, java.time.LocalDate studyDate);

    Page<Examination> findByStatus(ExaminationStatus status, Pageable pageable);
    Page<Examination> findByDoctorIdAndStatus(Long doctorId, ExaminationStatus status, Pageable pageable);

    Page<Examination> findByMaxPredictedGrade(Integer grade, Pageable pageable);
    Page<Examination> findByDoctorIdAndMaxPredictedGrade(Long doctorId, Integer grade, Pageable pageable);

    interface GradePatientCountProjection {
        Integer getGrade();
        Long getPatientCount();
    }

    @org.springframework.data.jpa.repository.Query(
        "SELECT e.maxPredictedGrade AS grade, COUNT(e.patient.id) AS patientCount " +
        "FROM Examination e " +
        "WHERE e.createdAt = (" +
        "    SELECT MAX(e2.createdAt) " +
        "    FROM Examination e2 " +
        "    WHERE e2.patient.id = e.patient.id" +
        ") " +
        "GROUP BY e.maxPredictedGrade"
    )
    List<GradePatientCountProjection> countPatientsByLatestGrade();

    @org.springframework.data.jpa.repository.Query(
        "SELECT e.maxPredictedGrade AS grade, COUNT(e.patient.id) AS patientCount " +
        "FROM Examination e " +
        "WHERE e.doctor.id = :doctorId AND e.createdAt = (" +
        "    SELECT MAX(e2.createdAt) " +
        "    FROM Examination e2 " +
        "    WHERE e2.patient.id = e.patient.id AND e2.doctor.id = :doctorId" +
        ") " +
        "GROUP BY e.maxPredictedGrade"
    )
    List<GradePatientCountProjection> countPatientsByLatestGradeForDoctor(@org.springframework.data.repository.query.Param("doctorId") Long doctorId);
}
