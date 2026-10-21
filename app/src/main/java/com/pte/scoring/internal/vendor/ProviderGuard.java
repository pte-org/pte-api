package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Retry and circuit breaker around one vendor's HTTP call. It wraps the single
 * request, not the job, so a speech answer split into chunks retries only the
 * chunk that failed and never pays again for the ones already scored.
 *
 * <p>A transient failure is retried after an exponential backoff (or the
 * provider's own retry hint) as long as the wait is shorter than what is left
 * of the job's {@link AiJobBudget}; a permanent one is thrown at once. After
 * {@code breakerThreshold} consecutive transient failures (not counting those
 * after the job's budget was already spent) the breaker opens
 * for a minute and calls fail fast with {@code PROVIDER_UNAVAILABLE} instead
 * of sending more paid requests to a provider that is down. After that minute
 * one failed call opens it again.
 */
public final class ProviderGuard {

    /** How the guard waits between attempts; tests pass one that only records the wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final Logger log = LoggerFactory.getLogger(ProviderGuard.class);
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
    private static final Duration OPEN_FOR = Duration.ofSeconds(60);
    // Outside a job (e.g. a manual spike run) nothing bounds the wait, so cap it.
    private static final Duration MAX_WAIT_WITHOUT_BUDGET = Duration.ofSeconds(30);
    private static final int STANDARD_MAX_ATTEMPTS = 3;
    private static final int STANDARD_BREAKER_THRESHOLD = 5;

    private final String vendor;
    private final int maxAttempts;
    private final int breakerThreshold;
    private final Clock clock;
    private final Sleeper sleeper;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openUntil = Instant.MIN;

    public ProviderGuard(String vendor, int maxAttempts, int breakerThreshold, Clock clock, Sleeper sleeper) {
        this.vendor = vendor;
        this.maxAttempts = maxAttempts;
        this.breakerThreshold = breakerThreshold;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public static ProviderGuard standard(String vendor, Clock clock) {
        return new ProviderGuard(vendor, STANDARD_MAX_ATTEMPTS, STANDARD_BREAKER_THRESHOLD, clock,
                duration -> Thread.sleep(duration));
    }

    /** One attempt and no breaker — the behaviour of a client built without a guard. */
    public static ProviderGuard passThrough(String vendor) {
        return new ProviderGuard(vendor, 1, Integer.MAX_VALUE, Clock.systemUTC(), duration -> {
        });
    }

    public <T> T call(Supplier<T> request) {
        for (int attempt = 1; ; attempt++) {
            if (isOpen()) {
                throw new AiProviderException(AiFailureReason.PROVIDER_UNAVAILABLE, vendor + " circuit open");
            }
            try {
                T result = request.get();
                consecutiveFailures.set(0);
                return result;
            } catch (AiProviderException ex) {
                if (!ex.isTransient()) {
                    consecutiveFailures.set(0); // The provider answered; it is up.
                    throw ex;
                }
                if (budgetSpent()) {
                    throw ex; // The job ran out of time, which says nothing about the provider.
                }
                recordTransientFailure();
                Duration wait = ex.getRetryAfter() != null ? ex.getRetryAfter() : backoff(attempt);
                if (attempt >= maxAttempts || isOpen() || !fitsTheBudget(wait)) {
                    throw ex;
                }
                log.warn("{} {} on attempt {}; retrying in {} ms", vendor, ex.getReason(), attempt, wait.toMillis());
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private boolean isOpen() {
        return clock.instant().isBefore(openUntil);
    }

    private void recordTransientFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= breakerThreshold) {
            openUntil = clock.instant().plus(OPEN_FOR);
            log.warn("{} circuit open for {} s after {} consecutive transient failures",
                    vendor, OPEN_FOR.toSeconds(), failures);
        }
    }

    private static boolean budgetSpent() {
        return AiJobBudget.remaining().filter(Duration::isZero).isPresent();
    }

    private boolean fitsTheBudget(Duration wait) {
        return wait.compareTo(AiJobBudget.remaining().orElse(MAX_WAIT_WITHOUT_BUDGET)) < 0;
    }

    private static Duration backoff(int attempt) {
        Duration backoff = FIRST_BACKOFF.multipliedBy(1L << Math.min(attempt - 1, 10));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }
}
