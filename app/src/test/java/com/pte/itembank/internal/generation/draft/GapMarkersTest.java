package com.pte.itembank.internal.generation.draft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The exam client splits a passage on {{n}} markers, so every way a model marks a blank must end up as one. */
class GapMarkersTest {

    @Test
    void numbersTheMarkersInReadingOrder() {
        assertThat(GapMarkers.normalize("A __BLANK__ and a [BLANK] and ____ and {{}} and {{7}}."))
                .isEqualTo("A {{0}} and a {{1}} and {{2}} and {{3}} and {{4}}.");
    }

    @Test
    void leavesAPassageWithoutBlanksAlone() {
        assertThat(GapMarkers.normalize("No blanks here.")).isEqualTo("No blanks here.");
        assertThat(GapMarkers.count("No blanks here.")).isZero();
    }

    @Test
    void countsTheBlanksWhateverTheirForm() {
        assertThat(GapMarkers.count("x __BLANK__ y {{3}} z ___")).isEqualTo(3);
    }

    @Test
    void handlesNull() {
        assertThat(GapMarkers.normalize(null)).isNull();
        assertThat(GapMarkers.count(null)).isZero();
    }
}
