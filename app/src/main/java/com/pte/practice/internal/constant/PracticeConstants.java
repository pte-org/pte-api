package com.pte.practice.internal.constant;

/** Stable practice-auth, entitlement and validation constants. */
public final class PracticeConstants {

    public static final String PRACTICE_AUTH_BASE_PATH = "/api/v1/auth/practice";
    public static final String PRACTICE_ENTITLEMENT_PATH = "/api/v1/student/practice/entitlement";
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
