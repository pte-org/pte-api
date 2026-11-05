package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerScoreException;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TraitSource;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExaminerTraitValidator {
    public Map<String, Integer> validate(EffectiveRubric rubric, Map<String, Integer> requested,
            Map<String, Integer> ai, boolean machineAvailable, Integer wordCount, Integer formBand) {
        if (requested == null) throw invalid(ExaminerScoringConstants.TRAITS_REQUIRED);
        if (requested.size() > RubricConstants.MAX_TRAIT_SCORE_ENTRIES) throw invalid(ExaminerScoringConstants.TRAIT_COUNT_INVALID);
        for (String key : requested.keySet()) {
            if (RubricTraits.FORM.equals(key) || rubric.task().trait(key).isEmpty()) throw invalid(ExaminerScoringConstants.TRAIT_UNKNOWN);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var trait : rubric.task().traits()) {
            Integer value;
            if (RubricTraits.FORM.equals(trait.key())) value = formBand;
            else if (trait.source() == TraitSource.AZURE) {
                if (requested.get(trait.key()) != null) throw invalid(ExaminerScoringConstants.MACHINE_TRAIT_READ_ONLY);
                if (!machineAvailable || ai.get(trait.key()) == null) throw invalid(ExaminerScoringConstants.AI_TRAITS_UNAVAILABLE);
                value = ai.get(trait.key());
            } else value = requested.get(trait.key());
            if (value == null || value < 0 || value > rubric.maxOf(trait, wordCount)) throw invalid(ExaminerScoringConstants.TRAIT_INVALID);
            result.put(trait.key(), value);
        }
        return result;
    }
    private static InvalidExaminerScoreException invalid(String code) { return new InvalidExaminerScoreException(code); }
}
