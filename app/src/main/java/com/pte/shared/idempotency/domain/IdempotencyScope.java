package com.pte.shared.idempotency.domain;

import com.pte.shared.idempotency.internal.constant.IdempotencyConstants;

import java.time.Duration;
import java.time.Instant;

/**
 * Command families that need dedupe + receipt. Retention is declared per scope; the anchor is
 * the session close time for session-bound scopes and the request time for the rest.
 */
public enum IdempotencyScope {
    ATTENDANCE(IdempotencyConstants.RETENTION_SESSION_BOUND),
    EXAMINER_COMMAND(IdempotencyConstants.RETENTION_SESSION_BOUND),
    LICENSE_ISSUE(IdempotencyConstants.RETENTION_LICENSE_ISSUE),
    PRACTICE_SESSION_OP(IdempotencyConstants.RETENTION_PRACTICE_OP);

    private final Duration retention;

    IdempotencyScope(Duration retention) {
        this.retention = retention;
    }

    public Instant expiresAt(Instant anchor) {
        return anchor.plus(retention);
    }
}
