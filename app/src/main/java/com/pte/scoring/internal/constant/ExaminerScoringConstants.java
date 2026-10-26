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
    public static final String AUDIT_AGGREGATE_TYPE = "EXAMINER_WORK";
    public static final String SHA_256_UNAVAILABLE = "SHA-256 algorithm not available";
    public static final String WORK_NOT_STARTED_FOR_DRAFT = "Work must be started before saving drafts";
    public static final String WORK_FINALIZED_NO_DRAFTS = "Cannot save drafts on finalized work";
    public static final String WORK_VERSION_MISMATCH = "Expected work version mismatch";
    public static final String ANSWER_NOT_IN_ATTEMPT = "Answer not found in assigned attempt";
    public static final String ANSWER_NOT_FOUND = "Answer not found";
    public static final String CONFIRM_AI_DRAFT_MISMATCH = "CONFIRM_AI score or version does not match active AI score";
    public static final String DRAFT_VERSION_MISMATCH_UNPERSISTED = "Expected draft version mismatch for unpersisted draft";
    public static final String ANSWER_ALREADY_SUBMITTED = "Answer already has a submitted examiner score";
    public static final String COHORT_NOT_FOUND = "Cohort not found";
    public static final String COHORT_NOT_MANUAL_FROZEN = "Cohort is not in FROZEN or COMPLETED manual examiner mode";
    public static final String ATTEMPT_NOT_IN_COHORT = "Attempt is not in grading cohort";
    public static final String ATTEMPT_EXCLUDED_FROM_GRADING_COHORT = "Attempt is excluded from grading cohort";
    public static final String WORK_NOT_STARTED_FOR_FINALIZE = "Work must be started before finalization";
    public static final String WORK_ALREADY_FINALIZED = "Work is already finalized";
    public static final String BLOCKING_ISSUES_OPEN = "Unresolved blocking issues exist";
    public static final String ELIGIBLE_POOL_EMPTY = "Eligible answer pool is empty";
    public static final String MANIFEST_POOL_MISMATCH = "Finalize manifest does not match eligible answer pool exactly";
    public static final String ASSIGNMENT_NOT_FOUND = "Assignment not found or unauthorized";
    public static final String SESSION_SCORES_PUBLISHED = "Session scores already published";
    public static final String AUDIT_SCORE_UPDATED = "Score updated";
    public static final String AUDIT_DRAFT_SAVED = "Draft saved";
    public static final String DRAFT_VERSION_MISMATCH_FORMAT = "Expected draft version mismatch: expected %d but was %d";
    public static final String DRAFT_INCOMPLETE_FORMAT = "Missing complete draft for answer %s";
    public static final String DRAFT_STALE_FORMAT = "Draft version mismatch for answer %s";
    public static final String CONFIRM_AI_FINALIZE_MISMATCH_FORMAT =
            "CONFIRM_AI score or version does not match active AI score for answer %s";
    public static final String UNSTARTED_VERSION_FORMAT =
            "Expected work version mismatch for unstarted work: expected %d but was 0";
    public static final String WORK_VERSION_FORMAT = "Expected work version mismatch: expected %d but was %d";
    public static final String WORK_NOT_STARTED_FOR_ISSUES = "Work must be started before reporting issues";
    public static final String WORK_FINALIZED_NO_ISSUES = "Cannot report issues on finalized work";
    public static final String ASSIGNMENT_OF_ISSUE_NOT_FOUND = "Assignment of the grading issue not found";
    public static final String COMMAND_RECEIPT_NOT_FOUND = "Command receipt not found";
    public static final String GRADING_ISSUE_NOT_FOUND = "Grading issue not found";
    public static final String AUDIT_START = "Grading started";
    public static final String AUDIT_REGRADE = "Grading reopened for regrade";
    public static final String AUDIT_START_PREFIX = "Grading started on attempt ";
    public static final String AUDIT_REGRADE_PREFIX = "Grading reopened for regrade on attempt ";
    public static final String AUDIT_ISSUE_REPORTED_PREFIX = "Reported issue ";
    public static final String ON_ATTEMPT = " on attempt ";
    public static final String EXAMINER_ROLE = "EXAMINER";
    public static final String EXAMINER_SOURCE = "EXAMINER";
    public static final String FINGERPRINT_SAVE_DRAFT = "SAVE_DRAFT:";
    public static final String FINGERPRINT_FINALIZE = "FINALIZE:";
    public static final String FINGERPRINT_SCORE = "SCORE:";
    public static final String FINGERPRINT_START = "START:";
    public static final String FINGERPRINT_REPORT_ISSUE = "REPORT_ISSUE:";
    public static final String FINALIZE_CHILD_KEY_PREFIX = "examiner-finalize-v1:";
    public static final String AUDIT_DRAFT_SUMMARY_FORMAT = "Examiner %s %s for answer %s: score=%s (prev=%s)";
    public static final String AUDIT_FINALIZED_SUMMARY_FORMAT = "Finalized grading for attempt %s (%d answers)";
    public static final String AUDIT_FINALIZED_REASON_FORMAT = "Finalized grading with %d answers";
    public static final String AUDIT_RESOLVED_ISSUE_FORMAT = "Resolved issue %s on attempt %s";
    public static final String AUDIT_ISSUE_REASON_FORMAT = "%s: %s";
    public static final String AUDIT_RESOLUTION_PREFIX = "Resolved issue: ";
    public static final long MEDIA_URL_TTL_SECONDS = 900;

    private ExaminerScoringConstants() {
    }
}
