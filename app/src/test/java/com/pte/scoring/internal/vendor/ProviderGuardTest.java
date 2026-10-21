package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderGuardTest {

    private static final String VENDOR = "AZURE_SPEECH";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T10:00:00Z"));
    private final List<Duration> waits = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void transientFailureIsRetriedAfterBackoff() {
        ProviderGuard guard = guard(3, 5);

        String result = guard.call(failingTimes(1, unavailable()));

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void providerRetryHintReplacesTheBackoff() {
        ProviderGuard guard = guard(3, 5);

        String result = guard.call(failingTimes(1, rateLimited(Duration.ofSeconds(7))));

        assertThat(result).isEqualTo("ok");
        assertThat(waits).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void permanentFailureIsNotRetried() {
        ProviderGuard guard = guard(3, 5);
        AiProviderException auth = new AiProviderException(AiFailureReason.PROVIDER_AUTH_FAILED, "HTTP 401");

        assertThatThrownBy(() -> guard.call(failingTimes(5, auth))).isSameAs(auth);

        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void givesUpAfterMaxAttemptsWithExponentialBackoff() {
        ProviderGuard guard = guard(3, 10);
        AiProviderException timeout = new AiProviderException(AiFailureReason.PROVIDER_TIMEOUT, "timeout");

        assertThatThrownBy(() -> guard.call(failingTimes(10, timeout))).isSameAs(timeout);

        assertThat(calls).hasValue(3);
        assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void waitThatDoesNotFitTheJobBudgetFailsWithoutSleeping() {
        ProviderGuard guard = guard(3, 5);
        AiProviderException limited = rateLimited(Duration.ofSeconds(5));

        assertThatThrownBy(() -> AiJobBudget.run(clock, Duration.ofSeconds(3),
                () -> guard.call(failingTimes(1, limited)))).isSameAs(limited);

        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void waitIsCappedWhenNoJobBudgetIsActive() {
        ProviderGuard guard = guard(3, 5);
        AiProviderException limited = rateLimited(Duration.ofSeconds(31));

        assertThatThrownBy(() -> guard.call(failingTimes(1, limited))).isSameAs(limited);

        assertThat(waits).isEmpty();
    }

    @Test
    void breakerOpensAfterConsecutiveTransientFailuresAndClosesAfterTheCooldown() {
        ProviderGuard guard = guard(1, 5);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> guard.call(failingTimes(1, unavailable())))
                    .isInstanceOf(AiProviderException.class);
        }
        calls.set(0);

        assertThatThrownBy(() -> guard.call(succeeding()))
                .isInstanceOfSatisfying(AiProviderException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_UNAVAILABLE))
                .hasMessageContaining(VENDOR);
        assertThat(calls).hasValue(0);

        clock.advance(Duration.ofSeconds(60));
        assertThat(guard.call(succeeding())).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void successResetsTheConsecutiveFailureCount() {
        ProviderGuard guard = guard(1, 5);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.call(failingTimes(1, unavailable())));
        }
        guard.call(succeeding());
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.call(failingTimes(1, unavailable())));
        }
        calls.set(0);

        assertThat(guard.call(succeeding())).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void permanentFailuresDoNotOpenTheBreaker() {
        ProviderGuard guard = guard(1, 2);
        AiProviderException badRequest = new AiProviderException(AiFailureReason.PROVIDER_BAD_REQUEST, "HTTP 400");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> guard.call(failingTimes(1, badRequest))).isSameAs(badRequest);
        }
        calls.set(0);

        assertThat(guard.call(succeeding())).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void failuresOnceTheJobBudgetIsSpentDoNotOpenTheBreaker() {
        ProviderGuard guard = guard(1, 1);
        AiProviderException timeout = new AiProviderException(AiFailureReason.PROVIDER_TIMEOUT, "budget spent");

        assertThatThrownBy(() -> AiJobBudget.run(clock, Duration.ZERO,
                () -> guard.call(failingTimes(1, timeout)))).isSameAs(timeout);
        calls.set(0);

        assertThat(guard.call(succeeding())).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void interruptedWaitRestoresTheFlagAndRethrows() {
        ProviderGuard guard = new ProviderGuard(VENDOR, 3, 5, clock, wait -> {
            throw new InterruptedException();
        });
        AiProviderException unavailable = unavailable();

        assertThatThrownBy(() -> guard.call(failingTimes(1, unavailable))).isSameAs(unavailable);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void passThroughCallsOnceAndNeverOpens() {
        ProviderGuard guard = ProviderGuard.passThrough(VENDOR);
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> guard.call(failingTimes(1, unavailable())));
        }
        assertThat(calls).hasValue(10);

        assertThat(guard.call(succeeding())).isEqualTo("ok");
    }

    private ProviderGuard guard(int maxAttempts, int breakerThreshold) {
        return new ProviderGuard(VENDOR, maxAttempts, breakerThreshold, clock, wait -> {
            waits.add(wait);
            clock.advance(wait);
        });
    }

    private Supplier<String> failingTimes(int failures, AiProviderException failure) {
        AtomicInteger remaining = new AtomicInteger(failures);
        return () -> {
            calls.incrementAndGet();
            if (remaining.getAndDecrement() > 0) {
                throw failure;
            }
            return "ok";
        };
    }

    private Supplier<String> succeeding() {
        return failingTimes(0, null);
    }

    private static AiProviderException unavailable() {
        return new AiProviderException(AiFailureReason.PROVIDER_UNAVAILABLE, VENDOR + " HTTP 503");
    }

    private static AiProviderException rateLimited(Duration retryAfter) {
        return new AiProviderException(AiFailureReason.PROVIDER_RATE_LIMITED, VENDOR + " HTTP 429", retryAfter);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
