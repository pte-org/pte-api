package com.pte.identity.internal.constant;

/**
 * Centralized codes/labels for identity (no hardcoded strings in logic, per
 * standard). Error codes are machine-readable and returned in
 * {@code ApiResponse.message}.
 *
 * <p>Trimmed from the microservice-era {@code IamConstants}: no outbox
 * aggregate/event-type constants (no outbox in the monolith) and no incoming
 * tenant-event constants (see {@code UserService.me()} — tenant lookup moves
 * to an in-process call once Phase 03 ports {@code tenancy}, not an event
 * projection).
 */
public final class IdentityConstants {

    public static final String INVALID_LOGIN = "INVALID_LOGIN";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String FORBIDDEN_ROLE_ASSIGNMENT = "FORBIDDEN_ROLE_ASSIGNMENT";
    public static final String FORBIDDEN_PASSWORD_RESET = "FORBIDDEN_PASSWORD_RESET";
    public static final String DUPLICATE_EMAIL_IN_BATCH = "DUPLICATE_EMAIL_IN_BATCH";

    public static final String KEY_ID = "iam-rsa-key";
    public static final String TOKEN_ISSUER = "pte-iam";
    public static final long ACCESS_TOKEN_TTL_SECONDS = 900L;      // 15 minutes
    public static final long REFRESH_TOKEN_TTL_SECONDS = 604_800L; // 7 days

    private IdentityConstants() {
    }
}
