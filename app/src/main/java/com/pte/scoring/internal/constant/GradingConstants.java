package com.pte.scoring.internal.constant;

/** Stable codes and bounded display copy for post-CLOSED grading completion. */
public final class GradingConstants {
    public static final String TENANT_REQUIRED = "tenantId is required";
    public static final String SESSION_REQUIRED = "sessionPublicId is required";
    public static final String ACTOR_REQUIRED = "actorPublicId is required";
    public static final String COHORT_REQUIRED = "cohortPublicId is required";
    public static final String ATTEMPT_REQUIRED = "attemptPublicId is required";
    public static final String STUDENT_REQUIRED = "studentPublicId is required";
    public static final String ATTEMPT_STATUS_REQUIRED = "attemptStatus is required";
    public static final String PREVIEW_VERSION_REQUIRED = "previewVersion is required";
    public static final String MARKING_MODE_REQUIRED = "markingMode is required";
    public static final String FROZEN_AT_REQUIRED = "frozenAt is required";
    public static final String COMPLETED_AT_REQUIRED = "completedAt is required";

    public static final String INVALID_REQUEST = "GRADING_COHORT_INVALID_REQUEST";
    public static final String INVALID_REQUEST_MESSAGE = "The grading cohort request is invalid.";
    public static final String SESSION_NOT_CLOSED = "GRADING_COHORT_SESSION_NOT_CLOSED";
    public static final String SESSION_NOT_CLOSED_MESSAGE = "The exam session must be closed before grading is frozen.";
    public static final String PREVIEW_STALE = "GRADING_COHORT_PREVIEW_STALE";
    public static final String PREVIEW_STALE_MESSAGE = "The session changed after preview. Refresh the grading cohort.";
    public static final String COHORT_ALREADY_FINALIZED = "GRADING_COHORT_ALREADY_FINALIZED";
    public static final String COHORT_ALREADY_FINALIZED_MESSAGE = "The grading cohort for this session is already frozen.";
    public static final String MARKING_MODE_CONFLICT = "GRADING_MARKING_MODE_CONFLICT";
    public static final String MARKING_MODE_CONFLICT_MESSAGE =
            "Manual examiner assignments are already committed for this session.";
    public static final String OUTSTANDING_DISPOSITION_REQUIRED = "GRADING_COHORT_OUTSTANDING_DISPOSITION_REQUIRED";
    public static final String OUTSTANDING_DISPOSITION_REQUIRED_MESSAGE =
            "Every outstanding attempt needs an audited exclusion reason.";
    public static final String INVALID_DISPOSITION = "GRADING_COHORT_INVALID_DISPOSITION";
    public static final String INVALID_DISPOSITION_MESSAGE = "The outstanding attempt disposition is invalid.";
    public static final String NO_PAPERS = "GRADING_COHORT_NO_PAPERS";
    public static final String NO_PAPERS_MESSAGE = "At least one submitted paper is required for grading.";
    public static final String PINNED_COVERAGE_MISSING = "GRADING_PINNED_COVERAGE_MISSING";
    public static final String MISSING_EXPECTED_ANSWER = "GRADING_MISSING_EXPECTED_ANSWER";
    public static final String SCORING_METHOD_UNAVAILABLE = "GRADING_SCORING_METHOD_UNAVAILABLE";
    public static final String OBJECTIVE_SCORE_REQUIRED = "GRADING_OBJECTIVE_SCORE_REQUIRED";
    public static final String AI_SCORE_REQUIRED = "GRADING_REAL_AI_SCORE_REQUIRED";
    public static final String EXAMINER_ASSIGNMENT_REQUIRED = "GRADING_EXAMINER_ASSIGNMENT_REQUIRED";
    public static final String EXAMINER_SCORE_REQUIRED = "GRADING_EXAMINER_SCORE_REQUIRED";
    public static final String SHA256_UNAVAILABLE = "SHA-256 is required by the runtime";
    public static final String REASON_REQUIRED = "A reason is required.";
    public static final int DISPOSITION_REASON_LIMIT = 500;

    private GradingConstants() { }
}
