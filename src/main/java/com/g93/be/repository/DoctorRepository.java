package com.g93.be.repository;


import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import com.g93.be.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {
    List<Doctor> findAllByStatus(UserStatus status);
    java.util.Optional<Doctor> findByUsername(String username);
}
