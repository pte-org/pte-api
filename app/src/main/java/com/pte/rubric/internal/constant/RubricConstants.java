package com.pte.rubric.internal.constant;

/** Rubric module codes, lifecycle labels and bounded values. */
public final class RubricConstants {

    public static final String AUDIT_AGGREGATE_TYPE = "PRACTICE_RUBRIC";
    public static final String AUDIT_CREATED = "CREATED";
    public static final String AUDIT_CLONED = "CLONED";
    public static final String AUDIT_UPDATED = "UPDATED";
    public static final String AUDIT_SUBMITTED = "SUBMITTED_FOR_APPROVAL";
    public static final String AUDIT_REJECTED = "REJECTED_TO_DRAFT";
    public static final String AUDIT_PUBLISHED = "PUBLISHED";
    public static final String AUDIT_OVERRIDE_PUBLISHED = "OVERRIDE_PUBLISHED";
    public static final String AUDIT_RETIRED = "RETIRED";
    public static final String AUDIT_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String AUDIT_AUTHORIZATION_DENIED = "AUTHORIZATION_DENIED";

    public static final String RUBRIC_NOT_FOUND = "RUBRIC_NOT_FOUND";
    public static final String RUBRIC_NOT_DRAFT = "RUBRIC_NOT_DRAFT";
    public static final String RUBRIC_INVALID_TRANSITION = "RUBRIC_INVALID_TRANSITION";
    public static final String RUBRIC_VALIDATION_FAILED = "RUBRIC_VALIDATION_FAILED";
    public static final String RUBRIC_CONCURRENT_MODIFICATION = "RUBRIC_CONCURRENT_MODIFICATION";
    public static final String RUBRIC_REASON_REQUIRED = "RUBRIC_REASON_REQUIRED";
    public static final String RUBRIC_PUBLISH_FORBIDDEN = "RUBRIC_PUBLISH_FORBIDDEN";
    public static final String RUBRIC_DRAFT_WRITE_REQUIRED = "RUBRIC_DRAFT_WRITE_REQUIRED";
    public static final String RUBRIC_INVALID_DOCUMENT = "Rubric document is invalid or empty";
    public static final String RUBRIC_INVALID_CRITERION = "Rubric contains an unknown or duplicate criterion";
    public static final String RUBRIC_INVALID_LEVEL = "Rubric contains an invalid criterion level";
    public static final String RUBRIC_MISSING_ANCHOR = "Rubric is missing an approved anchor example";
    public static final String RUBRIC_INVALID_TASK_TYPE = "Rubric task type is not supported by the practice contract";
    public static final String RUBRIC_INVALID_STATUS = "Rubric status filter is invalid";
    public static final String RUBRIC_RUNTIME_INCOMPATIBLE = "Rubric runtime compatibility is incomplete";
    public static final String RUBRIC_PUBLISHED_READ_ONLY = "Published or retired rubric revisions are read-only";

    public static final int MAX_DOCUMENT_LENGTH = 1_000_000;

    private RubricConstants() {
    }
}
