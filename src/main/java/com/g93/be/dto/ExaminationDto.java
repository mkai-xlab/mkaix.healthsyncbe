package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for representing an Examination.
 * Contains details such as encounter code, status, study date,
 * referring physician, and associated patient information and images.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationDto {
    private Long examinationId;
    private String encounterCode;
    private String status;
    private LocalDate studyDate;
    private java.time.LocalTime studyTime;
    private LocalDateTime visitTime;
    private String thumbnailUrl;
    private String bodyPart;
    private String referringPhysician;
    private String chiefComplaint;
    private String clinicalNotes;
    private String priority;
    private String finalDiagnosis;
    private String description;
    private PatientResponse patient;
    private Long doctorId;
    private java.util.List<ExaminationImageDto> images;
    private Integer isViewed;
    private Integer maxPredictedGrade;
}
