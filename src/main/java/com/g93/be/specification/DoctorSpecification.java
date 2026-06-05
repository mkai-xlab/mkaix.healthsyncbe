package com.g93.be.specification;

import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class DoctorSpecification {

    /**
     * Builds a JPA Specification for Doctor entity based on search keyword, specialization, and status.
     *
     * @param keyword        The search term to match against code, name, email, phone, or specialization
     * @param specialization Exact or partial match for specialization
     * @param status         Exact match for UserStatus
     * @return A combined Specification object
     */
    public static Specification<Doctor> searchAndFilter(String keyword, String specialization, UserStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search by Keyword
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate codeMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("doctorCode")), likePattern);
                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), likePattern);
                Predicate emailMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likePattern);
                Predicate phoneMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), likePattern);
                Predicate specMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("specialization")), likePattern);
                
                predicates.add(criteriaBuilder.or(codeMatch, nameMatch, emailMatch, phoneMatch, specMatch));
            }

            // Filter by Specialization
            if (specialization != null && !specialization.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("specialization")), 
                        "%" + specialization.trim().toLowerCase() + "%"
                ));
            }

            // Filter by Status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
