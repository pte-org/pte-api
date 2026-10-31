package com.pte.scoring.domain;

/** Stable invariant messages for scoring-domain state transitions. */
public final class ScoringDomainConstants {

    public static final String RAW_SCORE_RANGE_INVALID = "rawScore must be between 0 and 100";
    public static final String EXAMINER_SCORE_RANGE_INVALID = "Examiner score must be between 0 and 100";
    public static final String EXAMINER_SCORE_IMMUTABLE = "Submitted Examiner score is immutable";
    public static final String EXAMINER_SCORES_IMMUTABLE = "Submitted Examiner scores are immutable";
    public static final String AI_SCORE_PROVENANCE_REQUIRED =
            "AI score provenance must identify its provider category and provider";
    public static final String SCORE_SOURCE_SELECTION_FIELDS_REQUIRED =
            "Score source selection requires source, actor, and timestamp";
    public static final String REAL_AI_SCORE_REQUIRED_FOR_SELECTION =
            "Only a real AI score with provenance can be selected";
    public static final String SUBMITTED_EXAMINER_SCORE_REQUIRED_FOR_SELECTION =
            "An Examiner score for this answer must be submitted before selection";
    public static final String SCORE_SOURCE_AUDIT_SCOPE_INVALID =
            "Score-source audit scope value does not match its scope";
    public static final String SCORE_SOURCE_AUDIT_COUNT_NEGATIVE = "Affected answer count cannot be negative";
    public static final String SCORE_SOURCE_AUDIT_PREVIOUS_COUNTS_NEGATIVE =
            "Previous score-source counts cannot be negative";
    public static final String SCORE_SOURCE_AUDIT_APPEND_ONLY = "Score-source audit rows are append-only";
    public static final String ASSIGNMENT_TENANT_REQUIRED = "tenantId is required";
    public static final String ASSIGNMENT_SESSION_REQUIRED = "sessionPublicId is required";
    public static final String ASSIGNMENT_ANSWER_COUNT_NEGATIVE = "eligibleAnswerCount cannot be negative";
    public static final String ASSIGNMENT_ATTEMPT_REQUIRED = "attemptPublicId is required";
    public static final String ASSIGNMENT_EXAMINER_REQUIRED = "examinerPublicId is required";
    public static final String ASSIGNMENT_FINALIZED_NOT_REASSIGNABLE = "A finalized grading cannot be reassigned";
    public static final String ASSIGNMENT_ACTOR_REQUIRED = "assignedByPublicId is required";
    public static final String ASSIGNMENT_TIME_REQUIRED = "assignedAt is required";
    public static final String AI_SCORE_PROVIDER_REQUIRED =
            "AI score provenance must identify its provider category and provider";
    public static final String AI_PROVENANCE_VALUE_TOO_LONG = "%s exceeds its maximum length of %d";
    public static final String AI_PROVIDER_FIELD = "AI provider";
    public static final String AI_MODEL_FIELD = "AI model";
    public static final String AI_PROVIDER_VERSION_FIELD = "AI provider version";
    public static final String SCORE_AUDIT_ACTOR_REQUIRED = "actorPublicId is required";
    public static final String SCORE_AUDIT_SCOPE_REQUIRED = "scope is required";
    public static final String SCORE_AUDIT_SOURCE_REQUIRED = "selectedSource is required";
    public static final String SCORE_AUDIT_TIME_REQUIRED = "occurredAt is required";
    public static final String EXAMINER_SCORE_ANSWER_REQUIRED = "answerPublicId is required";
    public static final String EXAMINER_SCORE_ATTEMPT_REQUIRED = "attemptPublicId is required";
    public static final String EXAMINER_SCORE_SESSION_REQUIRED = "sessionPublicId is required";
    public static final String EXAMINER_SCORE_TENANT_REQUIRED = "tenantId is required";
    public static final String EXAMINER_SCORE_EXAMINER_REQUIRED = "examinerPublicId is required";
    public static final String EXAMINER_SCORE_SUBMISSION_TIME_REQUIRED = "submittedAt is required";
    public static final String EXAMINER_RECEIPT_CONFLICT =
            "Request key has already been used with different parameters";
    public static final String EXAMINER_CONFIRM_AI_MISMATCH =
            "CONFIRM_AI score must match reviewed AI score and version";
    public static final String EXAMINER_DECISION_KIND_REQUIRED =
            "decisionKind is required for modern score submission";
    public static final String EXAMINER_EXPECTED_REVIEW_VERSION_REQUIRED =
            "expectedReviewVersion is required for modern score submission";
    public static final String EXAMINER_REQUEST_PUBLIC_ID_REQUIRED =
            "requestPublicId is required for modern score submission";
    public static final String EXAMINER_REVIEWED_AI_REQUIRED_FOR_CONFIRM =
            "reviewedAiScore and reviewedAiVersion are required when confirming AI score";

    public static final String EXAMINER_FINGERPRINT_REQUIRED = "requestFingerprint is required";

    private ScoringDomainConstants() {
    }
}
