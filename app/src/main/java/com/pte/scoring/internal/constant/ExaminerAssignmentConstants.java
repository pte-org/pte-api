package com.pte.scoring.internal.constant;

public final class ExaminerAssignmentConstants {

    public static final String INVALID_ASSIGNMENT = "INVALID_EXAMINER_ASSIGNMENT";
    public static final String INVALID_ASSIGNMENT_MESSAGE = "The Examiner assignment request is invalid.";

    public static final String ALREADY_ASSIGNED = "Examiners are already assigned for this exam.";
    public static final String ASSIGNMENT_MUST_COVER_EVERY_ATTEMPT =
            "Assign every eligible attempt to exactly one Examiner.";
    public static final String AI_ONLY_COHORT_REJECTED =
            "Examiner assignments cannot be confirmed for AI_ONLY grading cohorts.";
    public static final String EXAMINERS_MUST_BE_ACTIVE_AND_TENANT_SCOPED =
            "Every selected Examiner must be active and belong to this tenant.";
    public static final String NOT_ASSIGNED_YET = "Examiners are not assigned for this exam yet.";
    public static final String ATTEMPT_NOT_ASSIGNED = "This attempt has no Examiner assignment.";
    public static final String SAME_EXAMINER = "The attempt is already assigned to this Examiner.";
    public static final String GRADING_ALREADY_FINALIZED =
            "The Examiner has already finalized this attempt, so it can no longer be reassigned.";
    public static final String REASSIGN_ISSUE_RESOLUTION = "Host reassigned the Examiner";
    public static final String AUDIT_REASSIGN_SUMMARY_FORMAT =
            "Host %s reassigned attempt %s from examiner %s to %s (%d drafts carried over)";
    public static final String AUDIT_REASSIGN_REASON_FORMAT =
            "Reassigned from %s to %s; %d drafts carried over (scores: %s), %d open issues closed";
    public static final String HOST_ROLE = "HOST_ADMIN";
    public static final String HOST_REQUIRED = "A tenant HOST_ADMIN is required.";

    private ExaminerAssignmentConstants() {
    }
}
