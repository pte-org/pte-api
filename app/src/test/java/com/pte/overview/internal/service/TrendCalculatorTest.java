package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.TrendValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrendCalculatorTest {

    @ParameterizedTest(name = "current={0} previous={1} -> {2}%")
    @CsvSource({
            "150, 100, 50.0",
            "100, 100, 0.0",
            "50, 100, -50.0",
            "0, 5, -100.0",
            "4, 3, 33.3",
            "2, 3, -33.3",
            "1, 3, -66.7",
            "3000000000, 1000000000, 200.0",
            "9, 8, 12.5"
    })
    void changePctIsComputedByTheBackendAndRoundedToOneDecimal(long current, long previous, double expected) {
        TrendValue trend = TrendCalculator.of(current, previous);

        assertThat(trend.current()).isEqualTo(current);
        assertThat(trend.previous()).isEqualTo(previous);
        assertThat(trend.changePct()).isEqualTo(expected);
    }

    @Test
    void zeroPreviousYieldsNullChangeNeverAnInventedPercentage() {
        assertThat(TrendCalculator.of(5, 0).changePct()).isNull();
        assertThat(TrendCalculator.of(0, 0).changePct()).isNull();
        assertThat(TrendCalculator.of(5, 0).current()).isEqualTo(5);
        assertThat(TrendCalculator.of(5, 0).previous()).isZero();
    }

    @Test
    void roundingIsHalfUpAwayFromZeroOnExactTies() {
        // +0.05% and -0.05% sit exactly on the rounding tie of the first decimal.
        assertThat(TrendCalculator.of(2001, 2000).changePct()).isEqualTo(0.1);
        assertThat(TrendCalculator.of(1999, 2000).changePct()).isEqualTo(-0.1);
    }

    @Test
    void anUnchangedValueIsZeroPercentNotNull() {
        assertThat(TrendCalculator.of(7, 7).changePct()).isEqualTo(0.0);
    }
}
