package com.pte.reporting.internal.repository;

import com.pte.reporting.domain.AttemptReport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Specifications for tenant-scoped immutable report reads with optional date filters. */
public final class AttemptReportSpecifications {

    private AttemptReportSpecifications() {
    }

    public static Specification<AttemptReport> forStudent(UUID tenantId, UUID studentPublicId,
            Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.<UUID>get("tenantId"), tenantId));
            predicates.add(criteriaBuilder.equal(root.<UUID>get("studentPublicId"), studentPublicId));
            predicates.add(criteriaBuilder.equal(root.<Boolean>get("deleted"), false));
            predicates.add(criteriaBuilder.isTrue(root.<Boolean>get("published")));
            predicates.add(criteriaBuilder.isNotNull(root.get("reportSnapshotJson")));
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Instant>get("publishedAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.<Instant>get("publishedAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
