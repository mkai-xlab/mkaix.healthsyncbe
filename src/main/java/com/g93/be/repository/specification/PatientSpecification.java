package com.g93.be.repository.specification;

import com.g93.be.entity.Patient;
import com.g93.be.dto.PatientFilterRequest;
import com.g93.be.entity.Patient;
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

            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String kw = "%" + request.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), kw),
                        cb.like(cb.lower(root.get("patientCode")), kw)));
            }
            if (request.getDateOfBirth() != null) {
                predicates.add(cb.equal(root.get("dob"), request.getDateOfBirth()));
            }
            if (request.getGender() != null) {
                predicates.add(cb.equal(root.get("gender"), request.getGender()));
            }
            if (request.getPhone() != null && !request.getPhone().isBlank()) {
                predicates
                        .add(cb.like(cb.lower(root.get("phone")), "%" + request.getPhone().trim().toLowerCase() + "%"));
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                predicates
                        .add(cb.like(cb.lower(root.get("email")), "%" + request.getEmail().trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
