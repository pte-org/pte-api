package com.aptis.modules.questionbank.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.domain.Skill;

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
            Boolean isCurrent) {

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

            // Default to showing only current versions unless explicitly overridden
            if (isCurrent == null || isCurrent) {
                predicates.add(criteriaBuilder.isTrue(root.get("isCurrent")));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
