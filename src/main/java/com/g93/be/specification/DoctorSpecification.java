package com.g93.be.specification;


import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;

public class DoctorSpecification {

    /**
     * Builds a JPA Specification for Doctor entity based on search keyword and status.
     *
     * @param keyword        The search term to match against name, email, phone, or username
     * @param status         Exact match for UserStatus
     * @return A combined Specification object
     */
    public static Specification<Doctor> searchAndFilter(String keyword, String statusNotUsed, UserStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search by Keyword
            if (keyword != null && !keyword.trim().isEmpty()) {
                String cleanKeyword = keyword.trim().toLowerCase();
                String likePattern = "%" + cleanKeyword + "%";

                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), likePattern);
                Predicate emailMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likePattern);
                Predicate phoneMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), likePattern);
                Predicate usernameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likePattern);
                
                predicates.add(criteriaBuilder.or(nameMatch, emailMatch, phoneMatch, usernameMatch));
            }

            // Filter by Status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
