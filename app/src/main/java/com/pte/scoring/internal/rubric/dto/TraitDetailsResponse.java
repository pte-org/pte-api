package com.pte.scoring.internal.rubric.dto;

import java.util.List;

/** One trait: {@code maxScore} is null for a per-word scale (Read Aloud Content), whose maximum is the word count. */
public record TraitDetailsResponse(
        String key,
        String displayNameVi,
        String displayNameEn,
        Integer maxScore,
        boolean perWord,
        String source,
        int weight,
        List<LevelResponse> levels,
        int defaultWeight) {
}
