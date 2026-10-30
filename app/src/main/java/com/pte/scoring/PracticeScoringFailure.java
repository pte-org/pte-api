package com.pte.scoring;

/**
 * Why a practice answer could not be scored. The names are persisted by the
 * practice module as strings, so keep them stable.
 */
public enum PracticeScoringFailure {
    /** Rate limit, outage or network error at the vendor; a later retry may succeed. */
    PROVIDER_UNAVAILABLE(true),
    /** The job budget or a vendor call ran out of time; a later retry may succeed. */
    BUDGET_EXCEEDED(true),
    /** Auth, bad request, invalid response or blocked content; the same input fails again. */
    PROVIDER_REJECTED(false),
    /** The answer payload is empty or its audio cannot be scored. */
    INVALID_PAYLOAD(false),
    /** The task type does not exist or has no score (Personal Introduction). */
    UNSUPPORTED_TASK_TYPE(false),
    /** A failure outside a vendor call, or one the vendor gave no reason for. */
    INTERNAL(false);

    private final boolean retryable;

    PracticeScoringFailure(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
