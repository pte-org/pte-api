package com.pte.proctoring.internal.constant;

/** Centralized codes/labels for proctoring. */
public final class ProctorConstants {

    public static final String PROCTOR_SESSION_NOT_FOUND = "PROCTOR_SESSION_NOT_FOUND";
    public static final String PROCTOR_SESSION_NOT_ACTIVE = "PROCTOR_SESSION_NOT_ACTIVE";
    public static final String PROCTOR_ROLE_REQUIRED = "PROCTOR_ROLE_REQUIRED";
    public static final String ATTEMPT_REFERENCE_REQUIRED = "Attempt reference is required";
    public static final String VIOLATION_TYPE_REQUIRED = "Violation type is required";
    public static final String COMMAND_TYPE_REQUIRED = "Command type is required";
    public static final String STOMP_AUTH_HEADER_MISSING = "Missing or malformed Authorization header on STOMP CONNECT";
    public static final String STOMP_JWT_INVALID = "Invalid JWT on STOMP CONNECT";
    public static final String SHA256_UNAVAILABLE = "SHA-256 not available";
    public static final int SECURITY_AUDIT_DEFAULT_LIMIT = 50;
    public static final int SECURITY_AUDIT_MAX_LIMIT = 100;
    public static final String SECURITY_AUDIT_LIMIT_INVALID = "SECURITY_AUDIT_LIMIT_INVALID";
    public static final String SECURITY_AUDIT_LIMIT_INVALID_MESSAGE =
            "Security audit limit must be between 1 and 100.";
    public static final String SECURITY_AUDIT_CURSOR_INVALID = "SECURITY_AUDIT_CURSOR_INVALID";
    public static final String SECURITY_AUDIT_CURSOR_INVALID_MESSAGE =
            "Security audit cursor is invalid or expired.";

    // STOMP
    public static final String TOPIC_PREFIX = "/topic/proctor-sessions/";

    private ProctorConstants() {
    }
}
