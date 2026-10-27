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
    public static final String HOST_ROLE = "HOST_ADMIN";
    public static final String HOST_REQUIRED = "A tenant HOST_ADMIN is required.";

    private ExaminerAssignmentConstants() {
    }
}
