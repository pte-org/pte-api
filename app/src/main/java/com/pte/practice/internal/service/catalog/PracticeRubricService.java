package com.pte.practice.internal.service.catalog;

import com.pte.practice.internal.dto.response.PracticeRubricResponse;
import com.pte.scoring.RubricCriteriaService;
import com.pte.scoring.rubric.EffectiveRubric;
import org.springframework.stereotype.Service;

import java.util.List;

/** Shows a student the criteria their speaking or writing answer is scored against; no weights or gates. */
@Service
public class PracticeRubricService {

    private final RubricCriteriaService criteria;

    public PracticeRubricService(RubricCriteriaService criteria) {
        this.criteria = criteria;
    }

    public PracticeRubricResponse rubricOf(String taskType) {
        return criteria.activeRubric(taskType)
                .map(rubric -> new PracticeRubricResponse(taskType, traitsOf(rubric)))
                .orElseGet(() -> new PracticeRubricResponse(taskType, List.of()));
    }

    private List<PracticeRubricResponse.Trait> traitsOf(EffectiveRubric rubric) {
        return rubric.task().traits().stream()
                .map(trait -> new PracticeRubricResponse.Trait(trait.key(), trait.displayNameVi(),
                        trait.displayNameEn(), trait.scale().isPerWord() ? null : rubric.maxOf(trait, null),
                        trait.levels().stream()
                                .map(level -> new PracticeRubricResponse.Level(level.level(), level.text()))
                                .toList()))
                .toList();
    }
}
