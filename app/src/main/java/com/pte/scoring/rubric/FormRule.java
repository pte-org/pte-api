package com.pte.scoring.rubric;

import java.util.List;

/**
 * The Form criterion of a writing task. {@link #bandFor} is the only place a Form band is decided: Pearson gives
 * Form 0 to a response in capital letters, without punctuation or made only of bullet points (Score Guide pp.33,
 * 35, 42), to a Summarize Written Text that is not one single sentence (p.33), and to any length outside the bands.
 */
public record FormRule(List<FormBand> bands, boolean requireSingleSentence, boolean applyMechanicalRules) {

    public FormRule {
        bands = List.copyOf(bands);
    }

    public int bandFor(TextMetrics metrics) {
        if (applyMechanicalRules && (metrics.allCaps() || !metrics.hasPunctuation() || metrics.bulletOnly())) {
            return 0;
        }
        if (requireSingleSentence && metrics.sentenceCount() != 1) {
            return 0;
        }
        return bands.stream().filter(band -> band.contains(metrics.wordCount()))
                .mapToInt(FormBand::band).max().orElse(0);
    }

    public int maxBand() {
        return bands.stream().mapToInt(FormBand::band).max().orElse(0);
    }

    public FormRule withBands(List<FormBand> replacement) {
        return new FormRule(replacement, requireSingleSentence, applyMechanicalRules);
    }
}
