package com.g93.be.repository;


import com.g93.be.entity.Patient;
import com.g93.be.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

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
}
