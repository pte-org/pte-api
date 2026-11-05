package com.pte.scoring.internal.rubric.speech;

import java.util.Comparator;
import java.util.List;

/**
 * One version of the table that turns Azure's 0-100 speech scores into the Pearson 0-5 bands of Pronunciation and
 * Oral Fluency (Score Guide pp.45-46). The thresholds are the team's proposal and have NOT been calibrated against
 * human raters ({@code calibrated = false}); every screen that shows them must say so. This is an approximation,
 * not Pearson's method.
 *
 * <p>Inputs: Pronunciation reads Azure {@code accuracy}; Oral Fluency reads the mean of Azure {@code fluency} and
 * {@code prosody}, or {@code fluency} alone when Azure returns no prosody (one rule for every task type, never a
 * prosody of 0). A missing Azure score counts as 0. A score below every threshold is band 0. The thresholds are
 * compared as given; nothing is rounded.
 *
 * @param pronunciation thresholds of bands 1..5, ascending
 * @param oralFluency   thresholds of bands 1..5, ascending
 */
public record AzureBandMapping(int version, boolean calibrated, List<AzureBandThreshold> pronunciation,
                               List<AzureBandThreshold> oralFluency) {

    public AzureBandMapping {
        pronunciation = sorted(pronunciation);
        oralFluency = sorted(oralFluency);
    }

    public int pronunciationBand(Double accuracy) {
        return bandOf(pronunciation, orZero(accuracy));
    }

    public int oralFluencyBand(Double fluency, Double prosody) {
        return bandOf(oralFluency, oralFluencyInput(fluency, prosody));
    }

    /** The single Oral Fluency input: fluency and prosody averaged, or fluency alone when prosody is missing. */
    public static double oralFluencyInput(Double fluency, Double prosody) {
        double fluencyScore = orZero(fluency);
        return prosody == null ? fluencyScore : (fluencyScore + prosody) / 2;
    }

    private static int bandOf(List<AzureBandThreshold> thresholds, double score) {
        int band = 0;
        for (AzureBandThreshold threshold : thresholds) {
            if (score >= threshold.minAzureScore()) {
                band = Math.max(band, threshold.band());
            }
        }
        return band;
    }

    private static double orZero(Double score) {
        return score == null ? 0 : score;
    }

    private static List<AzureBandThreshold> sorted(List<AzureBandThreshold> thresholds) {
        return thresholds.stream().sorted(Comparator.comparingInt(AzureBandThreshold::band)).toList();
    }
}
