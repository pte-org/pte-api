package com.pte.scoring.internal.vendor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The deadline of the AI job running on this thread, so code deep inside a
 * vendor client — {@link VendorHttp}'s wait and {@link ProviderGuard}'s retry
 * sleep — can stop before the job's time is up. The deadline is read with
 * the clock that set it.
 */
public final class AiJobBudget {

    private record Deadline(Clock clock, Instant at) {
    }

    private static final ThreadLocal<Deadline> DEADLINE = new ThreadLocal<>();

    private AiJobBudget() {
    }

    public static <T> T run(Clock clock, Duration budget, Supplier<T> work) {
        DEADLINE.set(new Deadline(clock, clock.instant().plus(budget)));
        try {
            return work.get();
        } finally {
            DEADLINE.remove();
        }
    }

    /** @return time left before the deadline (never negative), or empty outside a job */
    public static Optional<Duration> remaining() {
        Deadline deadline = DEADLINE.get();
        if (deadline == null) {
            return Optional.empty();
        }
        Duration left = Duration.between(deadline.clock().instant(), deadline.at());
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }
}
