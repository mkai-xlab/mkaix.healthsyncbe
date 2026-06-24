package com.g93.be.repository;

import com.g93.be.entity.DicomInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DicomInstanceRepository extends JpaRepository<DicomInstance, Long> {
    List<DicomInstance> findByExaminationId(Long examinationId);
    boolean existsBySopInstanceUid(String sopInstanceUid);
}
