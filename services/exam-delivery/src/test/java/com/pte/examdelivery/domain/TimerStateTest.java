package com.pte.examdelivery.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for a real bug found + fixed via a live end-to-end
 * walkthrough (plans/phat-speaking-api-e2e-verify Phase 3): with zero grace,
 * a recording-based answer could never successfully submit at all — the
 * client stops recording exactly at {@code responseDeadline} (matching the
 * time it was told), but the actual submission (upload + complete + answer
 * POST) is real network round-trip work that unavoidably lands after that
 * instant. Confirmed live: a genuine, successfully-uploaded recording was
 * rejected every time before this fix.
 */
class TimerStateTest {

    private TimerState stateWithResponseDeadline(Instant responseDeadline) {
        TimerState state = new TimerState();
        state.setResponseDeadline(responseDeadline);
        return state;
    }

    @Test
    void isResponseWindowExpired_exactlyAtDeadline_notExpired() {
        Instant now = Instant.now();
        TimerState state = stateWithResponseDeadline(now);

        assertThat(state.isResponseWindowExpired(now)).isFalse();
    }

    @Test
    void isResponseWindowExpired_withinGraceWindow_notExpired() {
        Instant now = Instant.now();
        TimerState state = stateWithResponseDeadline(now.minusSeconds(10)); // 10s past deadline

        assertThat(state.isResponseWindowExpired(now)).isFalse();
    }

    @Test
    void isResponseWindowExpired_atExactGraceBoundary_notExpired() {
        Instant now = Instant.now();
        TimerState state = stateWithResponseDeadline(now.minusSeconds(15)); // exactly at the 15s grace boundary

        assertThat(state.isResponseWindowExpired(now)).isFalse();
    }

    @Test
    void isResponseWindowExpired_pastGraceWindow_isExpired() {
        Instant now = Instant.now();
        TimerState state = stateWithResponseDeadline(now.minusSeconds(16)); // 1s past the grace window

        assertThat(state.isResponseWindowExpired(now)).isTrue();
    }

    @Test
    void isResponseWindowExpired_wellBeforeDeadline_notExpired() {
        Instant now = Instant.now();
        TimerState state = stateWithResponseDeadline(now.plusSeconds(60));

        assertThat(state.isResponseWindowExpired(now)).isFalse();
    }
}
