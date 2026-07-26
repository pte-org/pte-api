package com.pte.iam.constant;

/**
 * Centralized codes/labels for iam (no hardcoded strings in logic, per standard).
 * Error codes are machine-readable and returned in {@code ApiResponse.message}.
 */
public final class IamConstants {

    // Error codes
    public static final String INVALID_LOGIN = "INVALID_LOGIN";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED";
    public static final String CROSS_TENANT_ACCESS = "CROSS_TENANT_ACCESS";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String USER_SUSPENDED = "USER_SUSPENDED";
    public static final String FORBIDDEN_ROLE_ASSIGNMENT = "FORBIDDEN_ROLE_ASSIGNMENT";

    // Outbox aggregate + event types (produced by iam)
    public static final String AGGREGATE_USER = "User";
    public static final String EVENT_USER_CREATED = "UserCreated";
    public static final String EVENT_USER_SUSPENDED = "UserSuspended";

    // Incoming event types consumed from admin's outbox.event.Tenant topic
    // (Phase 6). String literals, not a shared Java constant — crossing a
    // service boundary, must match admin's AdminConstants values by wire
    // contract, not by code coupling.
    public static final String INCOMING_EVENT_TENANT_ONBOARDED = "TenantOnboarded";
    public static final String INCOMING_EVENT_TENANT_SUSPENDED = "TenantSuspended";
    public static final String TOPIC_TENANT_EVENTS = "outbox.event.Tenant";
    public static final String KAFKA_HEADER_EVENT_TYPE = "eventType";

    // JWT
    public static final String KEY_ID = "iam-rsa-key";
    public static final String TOKEN_ISSUER = "pte-iam";
    public static final long ACCESS_TOKEN_TTL_SECONDS = 900L;      // 15 minutes
    public static final long REFRESH_TOKEN_TTL_SECONDS = 604_800L; // 7 days

    private IamConstants() {
    }
}
