package com.pte.scoring.rubric;

/**
 * The countable facts about a written response that Form depends on. Counting happens elsewhere (FormCheck);
 * deciding the Form band from these facts happens only in {@link FormRule#bandFor}.
 */
public record TextMetrics(int wordCount, int sentenceCount, boolean allCaps, boolean hasPunctuation,
                          boolean bulletOnly) {
}
