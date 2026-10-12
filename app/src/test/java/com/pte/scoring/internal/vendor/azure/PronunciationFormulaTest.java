package com.pte.scoring.internal.vendor.azure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PronunciationFormulaTest {

    @Test
    void weighsAccuracyFortyPercentAndTheOtherThreeTwentyEach() {
        // 0.4*95 + 0.2*83 + 0.2*89 + 0.2*95
        assertThat(PronunciationFormula.score(95.0, 89.0, 83.0, 95.0)).isCloseTo(91.4, within(0.001));
    }

    @Test
    void reproducesThePocSamplesWithinTheirBand() {
        // POC single-shot pronunciation scores for Ver1..Ver3 were 89.0, 57.1 and 77.3.
        assertThat(PronunciationFormula.score(95.0, 89.0, 83.0, 95.0)).isCloseTo(89.0, within(5.0));
        assertThat(PronunciationFormula.score(48.0, 74.0, 67.3, 48.0)).isCloseTo(57.1, within(0.1));
        assertThat(PronunciationFormula.score(76.0, 87.0, 72.7, 78.0)).isCloseTo(77.3, within(1.0));
    }

    @Test
    void withoutProsodyTheOtherWeightsAreScaledUpToStillSumToOne() {
        // 0.5*80 + 0.25*60 + 0.25*100
        assertThat(PronunciationFormula.score(80.0, 60.0, null, 100.0)).isCloseTo(80.0, within(0.001));
    }

    @Test
    void aMissingScoreCountsAsZeroAndTheResultStaysInRange() {
        assertThat(PronunciationFormula.score(null, null, null, 0.0)).isZero();
        assertThat(PronunciationFormula.score(150.0, 150.0, 150.0, 150.0)).isEqualTo(100.0);
    }
}
