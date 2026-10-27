package com.pte.shared.idempotency.internal.constant;

import java.time.Duration;
import java.util.UUID;

public final class IdempotencyConstants {

    public static final Duration RETENTION_SESSION_BOUND = Duration.ofDays(90);
    public static final Duration RETENTION_LICENSE_ISSUE = Duration.ofDays(365);
    public static final Duration RETENTION_PRACTICE_OP = Duration.ofDays(7);
    public static final String IDEMPOTENCY_KEY_NOT_FOUND = "Idempotency key was not started: %s";
    public static final String IDEMPOTENCY_KEY_IN_FLIGHT = "IDEMPOTENCY_KEY_IN_FLIGHT";
    /** Stored instead of NULL for platform-level commands so the unique constraint also covers them. */
    public static final UUID PLATFORM_ORGANIZATION_ID = new UUID(0L, 0L);
    public static final String PURGE_CRON = "0 15 3 * * *";

    private IdempotencyConstants() {
    }
}
