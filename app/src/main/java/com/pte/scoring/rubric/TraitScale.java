package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;

/**
 * A trait's maximum score: fixed by the Score Guide (for example Content 0-6), or one point per word of the prompt
 * (Read Aloud Content, whose maximum "depends on the length of the question prompt"). {@code fixedMax} is null for
 * the per-word scale.
 */
public record TraitScale(Integer fixedMax) {

    public static TraitScale fixed(int max) {
        return new TraitScale(max);
    }

    public static TraitScale perWord() {
        return new TraitScale(null);
    }

    public boolean isPerWord() {
        return fixedMax == null;
    }

    /** The maximum for one answer; a per-word scale needs the prompt's word count, which must be at least 1. */
    public int maxFor(Integer wordCount) {
        if (!isPerWord()) {
            return fixedMax;
        }
        if (wordCount == null || wordCount < 1) {
            throw new RubricScoreException(RubricConstants.RUBRIC_WORD_COUNT_INVALID);
        }
        return wordCount;
    }
}
