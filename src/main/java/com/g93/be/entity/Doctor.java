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

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "degree", length = 100)
    private String degree;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;
}
