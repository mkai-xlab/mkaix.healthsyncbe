package com.g93.be.repository;

import com.g93.be.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {
    Optional<Patient> findByPatientCode(String patientCode);
    boolean existsByPatientCode(String patientCode);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}
