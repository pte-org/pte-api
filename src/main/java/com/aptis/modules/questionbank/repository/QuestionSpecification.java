package com.aptis.modules.questionbank.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.enums.PteTaskType;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds dynamic JPA Specification predicates for Question search queries.
 */
public final class QuestionSpecification {

    private QuestionSpecification() {
    }

    public static Specification<Question> buildFilter(
            Skill skill,
            Integer part,
            QuestionType questionType,
            DifficultyLevel difficultyLevel,
            QuestionStatus status,
            Boolean isCurrent,
            PteTaskType pteTaskType) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (skill != null) {
                predicates.add(criteriaBuilder.equal(root.get("skill"), skill));
            }
            if (part != null) {
                predicates.add(criteriaBuilder.equal(root.get("part"), part));
            }
            if (questionType != null) {
                predicates.add(criteriaBuilder.equal(root.get("questionType"), questionType));
            }
            if (difficultyLevel != null) {
                predicates.add(criteriaBuilder.equal(root.get("difficultyLevel"), difficultyLevel));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (pteTaskType != null) {
                predicates.add(criteriaBuilder.equal(root.get("pteTaskType"), pteTaskType));
            }

            // Default to showing only current versions unless explicitly overridden
            if (isCurrent == null || isCurrent) {
                predicates.add(criteriaBuilder.isTrue(root.get("isCurrent")));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Tenant visibility: Vendor-shared content (tenant_id IS NULL) is always visible;
     * Host-authored content is visible only to the Host's own tenant. A null
     * {@code callerTenantId} (Vendor/Admin caller) sees Vendor-shared content only —
     * Vendor has no standing authoring/moderation view into Host-private content.
     */
    public static Specification<Question> visibleTo(UUID callerTenantId) {
        return (root, query, criteriaBuilder) -> {
            if (callerTenantId == null) {
                return criteriaBuilder.isNull(root.get("tenantId"));
            }
            return criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("tenantId")),
                    criteriaBuilder.equal(root.get("tenantId"), callerTenantId));
        };
    }
}
