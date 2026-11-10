package com.pte.itembank.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StimulusLengthTest {

    @Test
    void usesTheFallbackWhenNothingWasMeasured() {
        assertThat(StimulusLength.median(List.of(), 85)).isEqualTo(85);
    }

    @Test
    void takesTheMiddleLengthOfAnOddCount() {
        assertThat(StimulusLength.median(List.of(90, 30, 60), 0)).isEqualTo(60);
    }

    @Test
    void averagesTheTwoMiddleLengthsOfAnEvenCount() {
        assertThat(StimulusLength.median(List.of(10, 20, 30, 41), 0)).isEqualTo(25);
    }
}
