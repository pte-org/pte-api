package com.pte.shared.audit.internal.repository;

import com.pte.shared.audit.domain.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the platform audit filter from only the values that are set. A static JPQL query with
 * {@code (:param is null or ...)} sends untyped nulls to PostgreSQL, which then fails to infer their type
 * ({@code lower(bytea)}, "could not determine data type of parameter"); adding a predicate per present
 * filter never binds a null.
 */
public final class PlatformAuditSpecifications {

    private static final String WILDCARD = "%";

    private PlatformAuditSpecifications() {
    }

    /** All arguments are optional; blank strings must already be normalized to null and search lower-cased. */
    public static Specification<AuditLog> platformRows(String aggregateType, String aggregateId, UUID actorId,
            String search, Instant createdFrom, Instant createdTo) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("tenantId")));
            if (aggregateType != null) {
                predicates.add(builder.equal(root.get("aggregateType"), aggregateType));
            }
            if (aggregateId != null) {
                predicates.add(builder.equal(root.get("aggregateId"), aggregateId));
            }
            if (actorId != null) {
                predicates.add(builder.equal(root.get("actorUserId"), actorId));
            }
            if (search != null) {
                String pattern = WILDCARD + search + WILDCARD;
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("aggregateType")), pattern),
                        builder.like(builder.lower(root.get("resourceName")), pattern),
                        builder.like(builder.lower(root.get("resourceCode")), pattern),
                        builder.like(builder.lower(root.get("action")), pattern),
                        builder.like(builder.lower(root.get("summary")), pattern)));
            }
            if (createdFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), createdTo));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
