package com.g93.be.repository.specification;

import com.g93.be.dto.PatientFilterRequest;
import com.g93.be.entity.Patient;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification builder for dynamic Patient filtering.
 */
public class PatientSpecification {

    public static Specification<Patient> filter(PatientFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getPatientCode() != null && !request.getPatientCode().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("patientCode")), "%" + request.getPatientCode().trim().toLowerCase() + "%"));
            }
            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                String cleanKeyword = request.getFullName().trim().toLowerCase();
                String keywordNoSpaces = cleanKeyword.replace(" ", "");
                
                Predicate standardMatch = cb.like(cb.lower(root.get("fullName")), "%" + cleanKeyword + "%");
                
                Expression<String> dbNameNoSpaces = cb.function("REPLACE", String.class, root.get("fullName"), cb.literal(" "), cb.literal(""));
                Predicate noSpaceMatch = cb.like(cb.lower(dbNameNoSpaces), "%" + keywordNoSpaces + "%");
                
                predicates.add(cb.or(standardMatch, noSpaceMatch));
            }
            if (request.getDateOfBirth() != null) {
                predicates.add(cb.equal(root.get("dateOfBirth"), request.getDateOfBirth()));
            }
            if (request.getGender() != null) {
                predicates.add(cb.equal(root.get("gender"), request.getGender()));
            }
            if (request.getPhone() != null && !request.getPhone().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("phone")), "%" + request.getPhone().trim().toLowerCase() + "%"));
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + request.getEmail().trim().toLowerCase() + "%"));
            }
            if (request.getAddress() != null && !request.getAddress().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("address")), "%" + request.getAddress().trim().toLowerCase() + "%"));
            }
            if (request.getEmergencyContactName() != null && !request.getEmergencyContactName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("emergencyContactName")), "%" + request.getEmergencyContactName().trim().toLowerCase() + "%"));
            }
            if (request.getEmergencyContactPhone() != null && !request.getEmergencyContactPhone().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("emergencyContactPhone")), "%" + request.getEmergencyContactPhone().trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
