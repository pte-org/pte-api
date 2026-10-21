package com.pte.scoring.internal.vendor.azure;

/**
 * Overall pronunciation score from the Azure sub-scores and the backend's own
 * completeness. Azure's single-shot overall score is unavailable for long
 * recordings, so the same weighting is applied to the merged sub-scores:
 * accuracy 40 %, fluency, prosody and completeness 20 % each. Without prosody
 * the remaining weights are scaled to sum to one.
 */
final class PronunciationFormula {

    private PronunciationFormula() {
    }

    static double score(Double accuracy, Double fluency, Double prosody, double completeness) {
        double accuracyScore = AzureScorers.orZero(accuracy);
        double fluencyScore = AzureScorers.orZero(fluency);
        double value = prosody == null
                ? 0.5 * accuracyScore + 0.25 * fluencyScore + 0.25 * completeness
                : 0.4 * accuracyScore + 0.2 * fluencyScore + 0.2 * prosody + 0.2 * completeness;
        return Math.max(0, Math.min(100, value));
    }
}
