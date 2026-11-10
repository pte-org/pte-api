package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerScoreException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact conversion at the request boundary; null is preserved for machine-only traits. Never rounds a band. */
public final class ExaminerTraitInputs {
    private ExaminerTraitInputs() { }

    public static Map<String, Integer> integers(Map<String, BigDecimal> input) {
        if (input == null) return null;
        if (input.size() > RubricConstants.MAX_TRAIT_SCORE_ENTRIES)
            throw new InvalidExaminerScoreException(ExaminerScoringConstants.TRAIT_COUNT_INVALID);
        Map<String, Integer> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            try { result.put(key, value == null ? null : value.intValueExact()); }
            catch (ArithmeticException invalid) { throw new InvalidExaminerScoreException(ExaminerScoringConstants.TRAIT_INVALID); }
        });
        return result;
    }
}
