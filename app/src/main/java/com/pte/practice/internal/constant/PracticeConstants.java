package com.pte.practice.internal.constant;

/** Stable practice-auth, catalog, session and validation constants. */
public final class PracticeConstants {

    public static final String PRACTICE_AUTH_BASE_PATH = "/api/v1/auth/practice";
    public static final String PRACTICE_ENTITLEMENT_PATH = "/api/v1/student/practice/entitlement";
    public static final String PRACTICE_CATALOG_PATH = "/api/v1/student/practice/catalog";
    public static final String PRACTICE_SESSION_PATH = "/api/v1/student/practice/sessions";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String PRACTICE_WEB_ENABLED_PROPERTY = "practice.web.enabled";
    public static final String PRACTICE_EMAIL_AUTH_ENABLED_PROPERTY = "practice.email-auth.enabled";
    public static final String PRACTICE_STRICT_ENTITLEMENT_ENABLED_PROPERTY =
            "practice.strict-entitlement.enabled";

    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String EMAIL_INVALID = "Email must be valid";
    public static final String CHALLENGE_ID_REQUIRED = "Challenge id is required";
    public static final String VERIFICATION_CODE_REQUIRED = "Verification code is required";
    public static final String VERIFICATION_CODE_INVALID = "Verification code must contain 6 digits";

    public static final String PRACTICE_INVALID_CHALLENGE = "PRACTICE_INVALID_CHALLENGE";
    public static final String PRACTICE_CHALLENGE_RATE_LIMITED = "PRACTICE_CHALLENGE_RATE_LIMITED";
    public static final String PRACTICE_NOT_ENTITLED = "PRACTICE_NOT_ENTITLED";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REQUIRED = "PRACTICE_IDEMPOTENCY_KEY_REQUIRED";
    public static final String PRACTICE_IDEMPOTENCY_KEY_INVALID = "PRACTICE_IDEMPOTENCY_KEY_INVALID";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REUSED = "PRACTICE_IDEMPOTENCY_KEY_REUSED";
    public static final String PRACTICE_STALE_SESSION_VERSION = "PRACTICE_STALE_SESSION_VERSION";
    public static final String PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
    public static final String PRACTICE_SESSION_NOT_STARTABLE = "PRACTICE_SESSION_NOT_STARTABLE";
    public static final String PRACTICE_SESSION_EXPIRED = "PRACTICE_SESSION_EXPIRED";
    public static final String PRACTICE_UNSUPPORTED_RUNTIME = "PRACTICE_UNSUPPORTED_RUNTIME";
    public static final String PRACTICE_CAPABILITY_MISMATCH = "PRACTICE_CAPABILITY_MISMATCH";
    public static final String PRACTICE_CONTENT_NOT_READY = "PRACTICE_CONTENT_NOT_READY";
    public static final String PRACTICE_CONFIDENCE_REQUIRED = "PRACTICE_CONFIDENCE_REQUIRED";
    public static final String PRACTICE_PRODUCT_NOT_FOUND = "PRACTICE_PRODUCT_NOT_FOUND";

    public static final String PRACTICE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE =
            "Retry this practice action with a stable Idempotency-Key.";
    public static final String PRACTICE_IDEMPOTENCY_KEY_INVALID_MESSAGE =
            "Idempotency-Key must be between 1 and 128 characters.";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REUSED_MESSAGE =
            "This request key was already used for a different practice request.";
    public static final String PRACTICE_STALE_SESSION_VERSION_MESSAGE =
            "This practice session changed in another tab. Reload before continuing.";
    public static final String PRACTICE_SESSION_NOT_STARTABLE_MESSAGE =
            "This practice session is not ready for that action.";
    public static final String PRACTICE_SESSION_EXPIRED_MESSAGE =
            "This practice session has expired.";
    public static final String PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE =
            "This practice task is not available in the current runtime.";
    public static final String PRACTICE_CAPABILITY_MISMATCH_MESSAGE =
            "This device does not support all capabilities required by the selected practice.";
    public static final String PRACTICE_CONTENT_NOT_READY_MESSAGE =
            "This practice content is not ready yet.";
    public static final String PRACTICE_CONFIDENCE_REQUIRED_MESSAGE =
            "Choose a confidence level before submitting an answered practice item.";
    public static final String PRACTICE_PRODUCT_NOT_FOUND_MESSAGE =
            "The requested practice product is not available.";

    public static final String PRACTICE_PRODUCT_CODE = "PTE_CORE_PRACTICE";
    public static final String PRACTICE_PRODUCT_TITLE = "PTE Core Practice";
    public static final String PRACTICE_DEFAULT_DISPLAY_NAME = "Practice";
    public static final String PRACTICE_WRITE_EMAIL_TASK_CODE = "WRITE_EMAIL";
    public static final String PRACTICE_WRITE_EMAIL_TASK_LABEL = "Write Email";
    public static final String PRACTICE_REFERENCE_GAP_PROVENANCE = "REFERENCE_GAP";
    public static final String PRACTICE_PERSISTED_CATALOG_PROVENANCE = "PERSISTED_CANONICAL_CATALOG";
    public static final String PRACTICE_RUNTIME_REGISTRY_PROVENANCE = "CANONICAL_RUNTIME_REGISTRY";
    public static final String PRACTICE_BLOCKED_CONTRACT_STATUS = "BLOCKED_CONTRACT";
    public static final String PRACTICE_RUNTIME_CONTRACT_READY_STATUS = "RUNTIME_CONTRACT_READY";
    public static final String PRACTICE_SERVER_READY_STATUS = "SERVER_READY";
    public static final String PRACTICE_SERVER_NOT_READY_STATUS = "SERVER_NOT_READY";
    public static final String PRACTICE_RUNTIME_ONLY_STATUS = "RUNTIME_ONLY_NO_PERSISTED_CONTENT";
    public static final String PRACTICE_NEXT_ACTION = "NEXT";
    public static final String PRACTICE_UNKNOWN_TASK_CODE = "UNKNOWN";
    public static final String PRACTICE_SHA256_UNAVAILABLE = "SHA-256 is unavailable";
    public static final String PRACTICE_CATALOG_VERSION = "2026.10";
    public static final int PRACTICE_TIME_LIMIT_SECONDS = 3_600;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public static final String PRACTICE_EMAIL_SUBJECT = "Your PTE Practice verification code";
    public static final String PRACTICE_EMAIL_BODY_TEMPLATE =
            "Your PTE Practice verification code is %s. It expires in %d minutes. "
                    + "If you did not request this code, you can ignore this email.";

    public static final int VERIFICATION_CODE_LENGTH = 6;
    public static final int VERIFICATION_CODE_TTL_MINUTES = 10;
    public static final int MAX_VERIFICATION_ATTEMPTS = 5;
    public static final int MAX_CHALLENGE_REQUESTS_PER_WINDOW = 3;
    public static final int CHALLENGE_REQUEST_WINDOW_MINUTES = 1;
    public static final int ENTITLEMENT_REFRESH_SECONDS = 300;

    private PracticeConstants() {
    }
}
