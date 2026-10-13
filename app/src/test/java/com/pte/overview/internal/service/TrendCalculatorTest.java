package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.TrendValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

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

    // ---- Phase 4: percentChange on exact BigDecimal amounts ----

    private static Double pct(String current, String previous) {
        return TrendCalculator.percentChange(new BigDecimal(current), new BigDecimal(previous));
    }

    @ParameterizedTest(name = "current={0} previous={1} -> {2}%")
    @CsvSource({
            "150.00, 100.00, 50.0",
            "100.00, 100.00, 0.0",
            "50.00, 100.00, -50.0",
            "0.00, 5.00, -100.0",
            "1500000.50, 1000000.00, 50.0",
            "120.00, 80.00, 50.0",
            "4, 3, 33.3",
            "2, 3, -33.3",
            "1, 3, -66.7",
            "9, 8, 12.5",
            "12345678901234567890.12, 12345678901234567890.12, 0.0",
            "0.02, 0.01, 100.0"
    })
    void amountChangePctIsComputedOnExactAmountsAndRoundedToOneDecimal(String current, String previous,
            double expected) {
        assertThat(pct(current, previous)).isEqualTo(expected);
    }

    @Test
    void amountChangeIsNullWhenThePreviousAmountIsZeroInAnyScale() {
        assertThat(pct("5.00", "0")).isNull();
        assertThat(pct("5.00", "0.00")).isNull();
        assertThat(pct("0.00", "0.0000")).isNull();
        assertThat(pct("-5.00", "0.00")).isNull();
    }

    @Test
    void amountChangeRoundsHalfUpAwayFromZeroOnExactTiesAndMatchesTheLongVariant() {
        // +0.05% / -0.05% sit exactly on the tie of the first decimal.
        assertThat(pct("2001.00", "2000.00")).isEqualTo(0.1);
        assertThat(pct("1999.00", "2000.00")).isEqualTo(-0.1);
        // The exact-amount path must agree with the long path wherever both apply.
        long[][] pairs = {{2001, 2000}, {1999, 2000}, {1, 3}, {2, 3}, {4, 3}, {9, 8}, {0, 7}, {7, 7}, {1, 1_000_000}};
        for (long[] pair : pairs) {
            assertThat(TrendCalculator.percentChange(BigDecimal.valueOf(pair[0]), BigDecimal.valueOf(pair[1])))
                    .as("%d vs %d", pair[0], pair[1]).isEqualTo(TrendCalculator.of(pair[0], pair[1]).changePct());
        }
    }

    @Test
    void amountChangeKeepsFractionalPrecisionThatALongCannotRepresent() {
        // 100.05 vs 100.00 is +0.05% -> 0.1; a long-based comparison of the integer parts would say 0.0.
        assertThat(pct("100.05", "100.00")).isEqualTo(0.1);
        assertThat(pct("100.04", "100.00")).isEqualTo(0.0);
        assertThat(pct("0.30", "0.10")).isEqualTo(200.0);
    }

    @Test
    void aNegativeChangeIsReportedWithItsSignAndAnAmountDroppingToZeroIsMinusOneHundred() {
        assertThat(pct("0.00", "1500000.50")).isEqualTo(-100.0);
        assertThat(pct("750000.25", "1500000.50")).isEqualTo(-50.0);
        assertThat(pct("1.00", "3.00")).isEqualTo(-66.7);
    }

    @Test
    void theLongVariantStillYieldsNullForAZeroPreviousAndTheSameValuesAsBefore() {
        assertThat(TrendCalculator.of(0, 0).changePct()).isNull();
        assertThat(TrendCalculator.of(5, 0).changePct()).isNull();
        assertThat(TrendCalculator.of(150, 100).changePct()).isEqualTo(50.0);
    }
}
