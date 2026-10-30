package com.pte.chat.internal.client;

/**
 * Why one chat call to the provider failed. Every reason is handled the same way by the caller (refund,
 * tell the student the system is busy, never retry); {@link #isTransient()} only feeds logs and metrics.
 */
public enum ChatAiFailureReason {

    TIMEOUT(true),
    NETWORK_ERROR(true),
    RATE_LIMITED(true),
    UNAVAILABLE(true),
    AUTH_FAILED(false),
    BAD_REQUEST(false),
    CONTENT_BLOCKED(false),
    EMPTY_RESPONSE(false),
    TRUNCATED(false),
    INVALID_RESPONSE(false);

    private final boolean transientFailure;

    ChatAiFailureReason(boolean transientFailure) {
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
