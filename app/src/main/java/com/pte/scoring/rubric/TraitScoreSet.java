package com.pte.scoring.rubric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The trait scores of one answer on the Pearson scale, plus the prompt's word count when a trait is scored per word
 * (Read Aloud Content). Stored as given so the item score can be recomputed later without calling the AI again.
 */
public record TraitScoreSet(Map<String, Integer> scores, Integer wordCount) {

    public TraitScoreSet {
        scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    }
}
