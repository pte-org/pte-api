package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiFailureReason;

import java.time.Duration;

/**
 * Explicit provider-boundary failure. {@link ProviderGuard} retries the
 * transient ones; whatever is left reaches the AI worker, which marks the
 * answer failed with {@link #getReason()} instead of inventing a score.
 *
 * <p>Messages carry classification codes only — never a provider response
 * body, transcript or essay — because they end up in logs.
 */
public class AiProviderException extends RuntimeException {

    private final AiFailureReason reason;
    private final Duration retryAfter;

    /** An unclassified failure — recorded as {@code INTERNAL_ERROR} and never retried. */
    public AiProviderException(String message) {
        this(AiFailureReason.INTERNAL_ERROR, message, null, null);
    }

    public AiProviderException(String message, Throwable cause) {
        this(AiFailureReason.INTERNAL_ERROR, message, null, cause);
    }

    public AiProviderException(AiFailureReason reason, String message) {
        this(reason, message, null, null);
    }

    // HTTP and transport failures are built only by VendorHttp, which keeps provider bodies out of them.
    AiProviderException(AiFailureReason reason, String message, Duration retryAfter) {
        this(reason, message, retryAfter, null);
    }

    AiProviderException(AiFailureReason reason, String message, Throwable cause) {
        this(reason, message, null, cause);
    }

    private AiProviderException(AiFailureReason reason, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public AiFailureReason getReason() {
        return reason;
    }

    /** Provider's retry hint (Retry-After / RetryInfo), or null when it gave none. */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    public boolean isTransient() {
        return reason.isTransient();
    }
}
