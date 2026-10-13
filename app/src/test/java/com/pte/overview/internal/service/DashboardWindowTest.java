package com.pte.overview.internal.service;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.exception.InvalidDashboardWindowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardWindowTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(ints = {7, 30, 90})
    void acceptsTheThreeSupportedWindows(int days) {
        DashboardWindow window = DashboardWindow.parse(String.valueOf(days), CLOCK);

        assertThat(window.days()).isEqualTo(days);
        assertThat(window.end()).isEqualTo(NOW);
        assertThat(window.start()).isEqualTo(NOW.minus(Duration.ofDays(days)));
        assertThat(window.previousStart()).isEqualTo(NOW.minus(Duration.ofDays(2L * days)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void missingOrBlankValueSelectsTheDefaultOfThirtyDays(String raw) {
        assertThat(DashboardWindow.parse(raw, CLOCK).days()).isEqualTo(30);
        assertThat(OverviewConstants.DEFAULT_WINDOW_DAYS).isEqualTo(30);
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertThat(DashboardWindow.parse(" 7 ", CLOCK).days()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "6", "8", "14", "29", "31", "60", "91", "365", "-7", "-30", "abc", "7d", "7.0",
            "7,0", "1e1", "0x1E", "NaN", "99999999999", "+7+", "7 7"})
    void anythingOutsideTheSupportedSetIsRejectedWithTheStableErrorCode(String raw) {
        assertThatThrownBy(() -> DashboardWindow.parse(raw, CLOCK))
                .isInstanceOfSatisfying(InvalidDashboardWindowException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("INVALID_DASHBOARD_WINDOW");
                    assertThat(ex.getCode()).isEqualTo(OverviewConstants.INVALID_DASHBOARD_WINDOW);
                });
    }

    @Test
    void currentAndPreviousPeriodsAreContiguousAndEquallyLong() {
        DashboardWindow window = DashboardWindow.parse("30", CLOCK);

        assertThat(Duration.between(window.previousStart(), window.start()))
                .isEqualTo(Duration.between(window.start(), window.end()))
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void windowIsAnchoredToTheInjectedClockNotToWallTime() {
        Instant later = NOW.plus(Duration.ofDays(5));

        DashboardWindow window = DashboardWindow.parse("7", Clock.fixed(later, ZoneOffset.UTC));

        assertThat(window.end()).isEqualTo(later);
        assertThat(window.start()).isEqualTo(later.minus(Duration.ofDays(7)));
    }

    @Test
    void supportedSetIsExactlySevenThirtyNinety() {
        assertThat(OverviewConstants.ALLOWED_WINDOW_DAYS).containsExactlyInAnyOrder(7, 30, 90);
    }
}
