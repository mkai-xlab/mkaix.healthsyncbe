package com.g93.be.repository;

import com.g93.be.entity.AiResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

@Repository
public interface AiResultRepository extends JpaRepository<AiResult, Long> {
        @Query("select r.aiAnalysis.dicomInstance.examination.doctor.id from AiResult r where r.id = :id")
        Optional<Long> findAssignedDoctorIdById(@Param("id") Long id);

        @Query("select r.aiAnalysis.dicomInstance.examination.doctor.id from AiResult r "
                        + "left join r.roiImage roiImage left join r.gradcamImage gradcamImage "
                        + "where roiImage.id = :imageId or gradcamImage.id = :imageId")
        Optional<Long> findAssignedDoctorIdByImageId(
                        @Param("imageId") Long imageId);
}
