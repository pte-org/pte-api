package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.rubric.ScoreTrace;
import java.util.Map;
import java.util.UUID;

public record ExaminerTraitScoreResult(Integer itemScore, ScoreTrace scoreTrace, Map<String, Integer> traits,
        UUID rubricVersionPublicId, String traitsJson, Integer wordCount) {
    public static ExaminerTraitScoreResult legacy(Integer score) {
        return new ExaminerTraitScoreResult(score, null, Map.of(), null, null, null);
    }
}
