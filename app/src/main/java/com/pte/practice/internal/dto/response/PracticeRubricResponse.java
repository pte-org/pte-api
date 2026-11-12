package com.pte.practice.internal.dto.response;

import java.util.List;

/** The criteria of one task type as the AI scores it: each trait with its scale and level descriptions. */
public record PracticeRubricResponse(String taskType, List<Trait> traits) {

    public PracticeRubricResponse {
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    /** {@code maxScore} is null for a per-word trait, whose maximum is the word count of each prompt. */
    public record Trait(String key, String displayNameVi, String displayNameEn, Integer maxScore, List<Level> levels) {
        public Trait {
            levels = levels == null ? List.of() : List.copyOf(levels);
        }
    }

    public record Level(int level, String text) {
    }
}
