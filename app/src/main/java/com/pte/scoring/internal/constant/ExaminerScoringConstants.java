package com.pte.scoring.internal.constant;

public final class ExaminerScoringConstants {

    public static final String WORK_NOT_FOUND = "EXAMINER_WORK_NOT_FOUND";
    public static final String INVALID_QUEUE_STATUS = "INVALID_EXAMINER_QUEUE_STATUS";
    public static final String SCORE_CONFLICT = "EXAMINER_SCORE_CONFLICT";
    public static final String INVALID_SCORE = "INVALID_EXAMINER_SCORE";
    public static final String OLD_PAYLOAD_UNSUPPORTED = "OLD_PAYLOAD_UNSUPPORTED";
    public static final String STALE_REVIEW_VERSION = "STALE_REVIEW_VERSION";
    public static final String MISSING_MANUAL_COHORT = "MISSING_MANUAL_COHORT";
    public static final String COHORT_NOT_FROZEN = "COHORT_NOT_FROZEN";
    public static final String INCOMPATIBLE_COHORT_MODE = "INCOMPATIBLE_COHORT_MODE";
    public static final String ATTEMPT_EXCLUDED_FROM_COHORT = "ATTEMPT_EXCLUDED_FROM_COHORT";
    public static final String EXCLUDED_COHORT_MEMBER = ATTEMPT_EXCLUDED_FROM_COHORT;
    public static final String SESSION_ALREADY_PUBLISHED = "SESSION_ALREADY_PUBLISHED";
    public static final String RECEIPT_CONFLICT = "RECEIPT_CONFLICT";
    public static final String CONFIRM_AI_MISMATCH = "CONFIRM_AI_MISMATCH";
    public static final String INVALID_DECISION_KIND = "INVALID_DECISION_KIND";
    public static final String AUTHENTICATED_TENANT_EXAMINER_REQUIRED =
            "An authenticated tenant Examiner is required";
    public static final long MEDIA_URL_TTL_SECONDS = 900;

    private ExaminerScoringConstants() {
    }
}
