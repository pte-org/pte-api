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
    public static final String SCORING_PUBLICATION_ALREADY_LOCKED =
            "Session scoring is already locked by another publication";
    public static final String SCORING_PUBLICATION_LOCK_IMMUTABLE = "Scoring publication locks cannot be removed";
    public static final String ASSIGNMENT_BATCH_TENANT_REQUIRED = "tenantId is required";
    public static final String ASSIGNMENT_BATCH_SESSION_REQUIRED = "sessionPublicId is required";
    public static final String ASSIGNMENT_BATCH_CREATOR_REQUIRED = "createdByPublicId is required";
    public static final String ASSIGNMENT_BATCH_MODE_REQUIRED = "mode is required";
    public static final String ASSIGNMENT_SCOPE_SNAPSHOT_REQUIRED = "scope snapshot is required";
    public static final String ASSIGNMENT_ALLOCATION_SNAPSHOT_REQUIRED = "assignment snapshot is required";
    public static final String ASSIGNMENT_PREVIEW_EXPIRY_REQUIRED = "preview expiry is required";
    public static final String ASSIGNMENT_COMMIT_TIME_REQUIRED = "commit timestamp is required";
    public static final String ASSIGNMENT_ANSWER_COUNT_NEGATIVE = "eligibleAnswerCount cannot be negative";
    public static final String ASSIGNMENT_REFERENCE_REQUIRED = "Assignment reference is required";
    public static final String ASSIGNMENT_ATTEMPT_REQUIRED = "attemptPublicId is required";
    public static final String ASSIGNMENT_EXAMINER_REQUIRED = "examinerPublicId is required";
    public static final String ASSIGNMENT_ACTOR_REQUIRED = "assignedByPublicId is required";
    public static final String ASSIGNMENT_TIME_REQUIRED = "assignedAt is required";
    public static final String COMMITTED_ASSIGNMENT_IMMUTABLE = "Committed Examiner assignments are immutable";
    public static final String ASSIGNMENT_BATCH_PREVIEW_REQUIRED =
            "Only a previewed assignment batch can be committed";
    public static final String AI_SCORE_PROVIDER_REQUIRED =
            "AI score provenance must identify its provider category and provider";
    public static final String AI_PROVENANCE_VALUE_TOO_LONG = "%s exceeds its maximum length of %d";
    public static final String AI_PROVIDER_FIELD = "AI provider";
    public static final String AI_MODEL_FIELD = "AI model";
    public static final String AI_PROVIDER_VERSION_FIELD = "AI provider version";
    public static final String SCORING_TENANT_REQUIRED = "tenantId is required";
    public static final String SCORING_SESSION_REQUIRED = "sessionPublicId is required";
    public static final String PUBLICATION_ID_REQUIRED = "publicationPublicId is required";
    public static final String PUBLICATION_LOCK_TIME_REQUIRED = "lockedAt is required";
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

    private ScoringDomainConstants() {
    }
}
