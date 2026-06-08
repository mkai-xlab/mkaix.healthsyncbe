package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

/**
 * Entity representing clinical DICOM metadata parsed from X-ray files.
 */
@Entity
@Table(name = "dicom_informations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DicomInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xray_image_id", nullable = false, unique = true)
    private XrayImage xrayImage;

    @Column(name = "sop_instance_uid", length = 150, nullable = false, unique = true)
    private String sopInstanceUid;

    @Column(name = "series_instance_uid", length = 150)
    private String seriesInstanceUid;

    @Column(name = "study_instance_uid", length = 150)
    private String studyInstanceUid;

    @Column(name = "patient_name", length = 150)
    private String patientName;

    @Column(name = "patient_id", length = 100)
    private String patientId;

    @Column(name = "patient_birth_date")
    private LocalDate patientBirthDate;

    @Column(name = "patient_sex", length = 10)
    private String patientSex;

    @Column(name = "modality", length = 50)
    private String modality;

    @Column(name = "manufacturer", length = 150)
    private String manufacturer;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "instance_number")
    private Integer instanceNumber;
}
