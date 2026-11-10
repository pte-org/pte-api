package com.pte.scoring.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Assignment-scoped declarations; no permission to use the vendor rubric API is granted. */
public record ExaminerRubricResponse(UUID rubricVersionPublicId, String taskType,
        List<Trait> traits, Map<String, Integer> mechanicalScores) {
    public record Trait(String key, String displayNameVi, String displayNameEn, int maxScore,
            String source, boolean editable, boolean useAiWhenNull, List<Level> levels) { }
    public record Level(int level, String text) { }
}
