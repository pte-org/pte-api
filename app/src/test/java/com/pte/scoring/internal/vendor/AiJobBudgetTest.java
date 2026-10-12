package com.pte.scoring.internal.vendor;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiJobBudgetTest {

    private static final Instant START = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void remainingIsEmptyOutsideABudget() {
        assertThat(AiJobBudget.remaining()).isEmpty();
    }

    @Test
    void remainingCountsDownOnTheClockThatSetTheDeadline() {
        Clock clock = clockReading(START, START.plusSeconds(30));

        Optional<Duration> remaining = AiJobBudget.run(clock, Duration.ofSeconds(90), AiJobBudget::remaining);

        assertThat(remaining).contains(Duration.ofSeconds(60));
        assertThat(AiJobBudget.remaining()).isEmpty();
    }

    @Test
    void remainingNeverGoesNegative() {
        Clock clock = clockReading(START, START.plusSeconds(120));

        Optional<Duration> remaining = AiJobBudget.run(clock, Duration.ofSeconds(90), AiJobBudget::remaining);

        assertThat(remaining).contains(Duration.ZERO);
    }

    @Test
    void budgetIsClearedEvenWhenTheWorkThrows() {
        assertThatThrownBy(() -> AiJobBudget.run(clockReading(START), Duration.ofSeconds(90), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(AiJobBudget.remaining()).isEmpty();
    }

    private static Clock clockReading(Instant first, Instant... then) {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(first, then);
        return clock;
    }
}
