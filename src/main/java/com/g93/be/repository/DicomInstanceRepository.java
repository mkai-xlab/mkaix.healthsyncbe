package com.g93.be.repository;

import com.g93.be.entity.DicomInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface DicomInstanceRepository extends JpaRepository<DicomInstance, Long> {
        List<DicomInstance> findByExaminationId(Long examinationId);

        boolean existsBySopInstanceUid(String sopInstanceUid);

        @Query("select d.examination.doctor.id from DicomInstance d where d.id = :id")
        Optional<Long> findAssignedDoctorIdById(@Param("id") Long id);

        @Query("select d.examination.doctor.id from DicomInstance d "
                        + "left join d.image image left join d.annotatedImage annotatedImage "
                        + "where image.id = :imageId or annotatedImage.id = :imageId")
        Optional<Long> findAssignedDoctorIdByImageId(
                        @Param("imageId") Long imageId);

        @Query("select d.examination.patient.id from DicomInstance d "
                        + "left join d.image image left join d.annotatedImage annotatedImage "
                        + "where image.id = :imageId or annotatedImage.id = :imageId")
        Optional<Long> findPatientIdByImageId(
                        @Param("imageId") Long imageId);

        @Query("SELECT COUNT(DISTINCT d.studyInstanceUid) FROM DicomInstance d")
        long countUniqueStudies();
}
