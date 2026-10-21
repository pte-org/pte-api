package com.pte.scoring.domain.enums;

/**
 * Why an AI vendor call failed. Transient reasons are provider-side hiccups
 * worth retrying; permanent ones fail the same way on every attempt, so a
 * retry only burns quota.
 */
public enum AiFailureReason {
    PROVIDER_RATE_LIMITED(true),
    PROVIDER_UNAVAILABLE(true),
    PROVIDER_TIMEOUT(true),
    PROVIDER_NETWORK_ERROR(true),
    PROVIDER_AUTH_FAILED(false),
    PROVIDER_BAD_REQUEST(false),
    PROVIDER_RESPONSE_INVALID(false),
    PROVIDER_CONTENT_BLOCKED(false),
    AUDIO_UNSUPPORTED(false),
    AUDIO_TOO_LONG(false),
    /** The job failed outside a vendor call (or the vendor gave no reason); retrying the same job won't help. */
    INTERNAL_ERROR(false);

    private final boolean transientFailure;

    AiFailureReason(boolean transientFailure) {
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
