package com.pte.proctoring.internal.constant;

/** Centralized codes/labels for proctoring. */
public final class ProctorConstants {

    public static final String PROCTOR_ROLE_REQUIRED = "PROCTOR_ROLE_REQUIRED";
    public static final String ATTEMPT_REFERENCE_REQUIRED = "Attempt reference is required";
    public static final String VIOLATION_TYPE_REQUIRED = "Violation type is required";
    public static final String COMMAND_TYPE_REQUIRED = "Command type is required";
    public static final String TARGET_ATTEMPT_INVALID = "TARGET_ATTEMPT_INVALID";
    public static final String STOMP_AUTH_HEADER_MISSING = "Missing or malformed Authorization header on STOMP CONNECT";
    public static final String STOMP_JWT_INVALID = "Invalid JWT on STOMP CONNECT";
    public static final int SECURITY_AUDIT_DEFAULT_LIMIT = 50;
    public static final int SECURITY_AUDIT_MAX_LIMIT = 100;
    public static final String SECURITY_AUDIT_LIMIT_INVALID = "SECURITY_AUDIT_LIMIT_INVALID";
    public static final String SECURITY_AUDIT_LIMIT_INVALID_MESSAGE =
            "Security audit limit must be between 1 and 100.";
    public static final String SECURITY_AUDIT_CURSOR_INVALID = "SECURITY_AUDIT_CURSOR_INVALID";
    public static final String SECURITY_AUDIT_CURSOR_INVALID_MESSAGE =
            "Security audit cursor is invalid or expired.";

    public static final String WATCH_STATUS_ACTIVE = "ACTIVE";
    public static final String PROCTOR_ROLE = "PROCTOR";
    public static final String PROCTOR_FLAG_SEVERITY = "WARNING";

    public static final String SECURITY_AUDIT_DISABLED = "SECURITY_AUDIT_DISABLED";
    public static final String SECURITY_AUDIT_POLICY_UNRESOLVED = "SECURITY_AUDIT_POLICY_UNRESOLVED";
    public static final String SECURITY_AUDIT_EVENT_CONFLICT = "SECURITY_AUDIT_EVENT_CONFLICT";
    public static final String SECURITY_CLIENT_EVENT_ID_REQUIRED = "Client event ID is required";
    public static final String SECURITY_CLIENT_EVENT_ID_TOO_LONG = "Client event ID must not exceed 128 characters";
    public static final String SECURITY_VIOLATION_TYPE_REQUIRED = "Violation type is required";
    public static final String SECURITY_DETAIL_TOO_LONG = "Security event detail must not exceed 2048 characters";
    public static final String SECURITY_AUDIT_DISABLED_MESSAGE =
            "Security audit is disabled for this attempt's policy.";
    public static final String SECURITY_AUDIT_POLICY_UNRESOLVED_MESSAGE =
            "The attempt security policy could not be resolved.";
    public static final String SECURITY_AUDIT_EVENT_CONFLICT_MESSAGE =
            "The client event ID was already used for a different security event.";
    public static final String CURSOR_PARTS_MISSING = "missing cursor parts";

    public static final String RETENTION_DAYS_INVALID = "security-audit retention-days must be positive";
    public static final String LOCKDOWN_MODE_STANDARD = "STANDARD";
    public static final String LOCKDOWN_MODE_STRICT = "STRICT";
    public static final String LOCKDOWN_MODE_NONE = "NONE";
    public static final String SESSION_ACCESS_DENIED = "Session access denied";
    public static final String STUDENT_NOT_ENROLLED_DENIED = "Student is not enrolled in this session";
    public static final String DEFAULT_STUDENT_NAME = "Thí sinh";

    // STOMP
    public static final String TOPIC_PREFIX = "/topic/proctor-sessions/";

    private ProctorConstants() {
    }
}
