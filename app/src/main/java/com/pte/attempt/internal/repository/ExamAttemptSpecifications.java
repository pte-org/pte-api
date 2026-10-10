package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Specifications for tenant-scoped attempt reads with optional history filters. */
public final class ExamAttemptSpecifications {

    private ExamAttemptSpecifications() {
    }

    public static Specification<ExamAttempt> forStudent(UUID tenantId, UUID studentPublicId,
            Instant from, Instant to, AttemptStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.<UUID>get("tenantId"), tenantId));
            predicates.add(criteriaBuilder.equal(root.<UUID>get("studentPublicId"), studentPublicId));
            predicates.add(criteriaBuilder.equal(root.<Boolean>get("deleted"), false));
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.<Instant>get("createdAt"), to));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.<AttemptStatus>get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
