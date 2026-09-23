package com.pte.scoring.internal.constant;

public final class ExaminerAssignmentConstants {

    public static final String INVALID_ASSIGNMENT = "INVALID_EXAMINER_ASSIGNMENT";
    public static final String INVALID_ASSIGNMENT_MESSAGE = "The Examiner assignment request is invalid.";
    public static final String STALE_PREVIEW = "EXAMINER_ASSIGNMENT_PREVIEW_STALE";

    public static final String INVALID_BATCH_PAGE = "Batch history page/size is outside the allowed range.";
    public static final String SCOPE_AND_MODE_REQUIRED = "At least one scope and an assignment mode are required.";
    public static final String SCOPE_TYPE_AND_ID_REQUIRED = "Each scope must specify its type and public ID.";
    public static final String MANUAL_EXAMINER_REQUIRED = "Manual mode requires an Examiner for every scope.";
    public static final String RANDOM_SCOPE_EXAMINER_FORBIDDEN =
            "Random mode uses one pooled Examiner list, not per-scope mappings.";
    public static final String DUPLICATE_SCOPE = "Duplicate scope in assignment request.";
    public static final String RANDOM_EXAMINERS_REQUIRED = "Random mode requires unique active Examiner IDs.";
    public static final String MANUAL_EXAMINER_LIST_FORBIDDEN =
            "Manual mode takes its Examiner from each scope mapping.";
    public static final String EXAMINERS_MUST_BE_ACTIVE_AND_TENANT_SCOPED =
            "Every selected Examiner must be active and belong to this tenant.";
    public static final String HOST_REQUIRED = "A tenant HOST_ADMIN is required.";
    public static final String SNAPSHOT_WRITE_FAILED = "Could not persist the assignment snapshot";
    public static final String SCOPE_SNAPSHOT_READ_FAILED = "Could not read the assignment scope snapshot";
    public static final String ALLOCATION_SNAPSHOT_READ_FAILED = "Could not read the assignment allocation snapshot";
    public static final String STALE_PREVIEW_MESSAGE =
            "The candidate pool changed after preview. Create a new preview before confirming.";

    private ExaminerAssignmentConstants() {
    }
}
