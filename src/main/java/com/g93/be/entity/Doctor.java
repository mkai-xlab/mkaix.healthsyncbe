package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doctors")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Doctor extends User {

    @Column(name = "doctor_code", length = 50, nullable = false, unique = true)
    private String doctorCode;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Column(name = "specialization", length = 150)
    private String specialization;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "academic_title", length = 100)
    private String academicTitle;

    @Column(name = "degree", length = 100)
    private String degree;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "position")
    private DoctorPosition position;
}
