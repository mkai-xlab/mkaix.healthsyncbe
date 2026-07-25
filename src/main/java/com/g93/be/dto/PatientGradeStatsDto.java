package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for returning patient statistics grouped by max predicted grade.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientGradeStatsDto {
    private Integer grade;
    private Long patientCount;
}
