package com.g93.be.repository;


import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.DicomInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DicomInstanceRepository extends JpaRepository<DicomInstance, Long> {
    List<DicomInstance> findByExaminationId(Long examinationId);
    boolean existsBySopInstanceUid(String sopInstanceUid);

    @org.springframework.data.jpa.repository.Query(
            "select d.examination.doctor.id from DicomInstance d where d.id = :id")
    Optional<Long> findAssignedDoctorIdById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query(
            "select d.examination.doctor.id from DicomInstance d "
                    + "left join d.image image left join d.annotatedImage annotatedImage "
                    + "where image.id = :imageId or annotatedImage.id = :imageId")
    Optional<Long> findAssignedDoctorIdByImageId(
            @org.springframework.data.repository.query.Param("imageId") Long imageId);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT d.studyInstanceUid) FROM DicomInstance d")
    long countUniqueStudies();
}
