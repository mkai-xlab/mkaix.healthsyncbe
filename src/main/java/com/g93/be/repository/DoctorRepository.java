package com.g93.be.repository;

import com.g93.be.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByDoctorCode(String doctorCode);
    Optional<Doctor> findByLicenseNumber(String licenseNumber);
    List<Doctor> findAllByStatus(com.g93.be.entity.UserStatus status);
}

