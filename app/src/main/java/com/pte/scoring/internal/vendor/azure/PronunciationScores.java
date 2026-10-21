package com.pte.scoring.internal.vendor.azure;

/** Azure's 0–100 pronunciation scores for one recognized segment; prosody may be absent. */
public record PronunciationScores(Double accuracy, Double fluency, Double completeness, Double prosody,
                                  Double pronunciation) {

    public static PronunciationScores zero() {
        return new PronunciationScores(0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
