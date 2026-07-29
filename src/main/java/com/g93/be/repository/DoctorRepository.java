package com.g93.be.repository;


import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import com.g93.be.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {
    @Override
    @EntityGraph(attributePaths = {"avatar", "role"})
    List<Doctor> findAll();

    @Override
    @EntityGraph(attributePaths = {"avatar", "role"})
    Page<Doctor> findAll(Specification<Doctor> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"avatar", "role"})
    List<Doctor> findAllByStatus(UserStatus status);

    java.util.Optional<Doctor> findByUsername(String username);

    @EntityGraph(attributePaths = {"avatar", "role"})
    Optional<Doctor> findProfileByUsername(String username);

    @EntityGraph(attributePaths = {"avatar", "role"})
    Optional<Doctor> findDetailsById(Long id);
}
