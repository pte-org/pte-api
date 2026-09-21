package com.pte.scoretemplate.internal.constant;

/** Centralized codes/labels for scoretemplate. */
public final class ScoreTemplateConstants {

    public static final String TEMPLATE_NOT_FOUND = "SCORE_TEMPLATE_NOT_FOUND";
    public static final String NO_ACTIVE_TEMPLATE = "NO_ACTIVE_SCORE_TEMPLATE";
    public static final String TEMPLATE_NOT_DRAFT_CODE = "SCORE_TEMPLATE_NOT_DRAFT";
    public static final String TEMPLATE_NOT_DRAFT = "This template version is locked and cannot be edited. Clone it to create a new editable version.";
    public static final String CONCURRENT_MODIFICATION = "SCORE_TEMPLATE_CONCURRENT_MODIFICATION";
    public static final String TEMPLATE_PENDING_APPROVAL = "SCORE_TEMPLATE_PENDING_APPROVAL";
    public static final String TEMPLATE_NOT_PENDING_APPROVAL = "SCORE_TEMPLATE_NOT_PENDING_APPROVAL";
    public static final String TEMPLATE_APPROVAL_REASON_REQUIRED = "Approval decision reason is required";
    public static final String TEMPLATE_TASK_TYPE_INVALID = "Template contains an unknown or inactive task type: ";
    public static final String TEMPLATE_SECTION_INVALID = "Template section does not match task type: ";
    public static final String TEMPLATE_PROFILE_INVALID = "Runtime profile is missing or not supported for task type: ";
    public static final String RUNTIME_PROFILE_NOT_PINNED = "RUNTIME_PROFILE_NOT_PINNED";
    public static final String RUNTIME_PROFILE_INVALID = "RUNTIME_PROFILE_INVALID";
    public static final String UNKNOWN_TASK_TYPE = "UNKNOWN_TASK_TYPE";

    public static final String NAME_REQUIRED = "Template name is required";
    public static final String ITEMS_REQUIRED = "A template needs at least one item";
    public static final String MISSING_TASK_TYPES = "Missing required task types: ";
    public static final String INVALID_COUNT_RANGE = "minCount/maxCount invalid for task type ";
    public static final String NEGATIVE_WEIGHT = "Weight cannot be negative for task type ";
    public static final String SKILL_WITH_NO_WEIGHT = "No task type contributes any weight to skill ";
    public static final String SKILL_WEIGHT_NOT_100 = "Skill weight column must total exactly 100 for ";
    public static final String DUPLICATE_TASK_TYPES = "Duplicate task types: ";
    public static final String DUPLICATE_SEQUENCES = "Duplicate template sequence values: ";
    public static final String INVALID_SEQUENCE = "Template sequence must be non-negative: ";
    public static final String TEMPLATE_VALIDATION_FAILED = "SCORE_TEMPLATE_VALIDATION_FAILED";

    public static final String AUDIT_AGGREGATE_TYPE = "SCORE_TEMPLATE";
    public static final String AUDIT_CREATED = "CREATED";
    public static final String AUDIT_CLONED = "CLONED";
    public static final String AUDIT_SUBMITTED = "SUBMITTED_FOR_APPROVAL";
    public static final String AUDIT_APPROVED = "APPROVED_FOR_ACTIVATION";
    public static final String AUDIT_REJECTED = "REJECTED_TO_DRAFT";
    public static final String AUDIT_ACTIVATED = "ACTIVATED";
    public static final String AUDIT_RETIRED = "RETIRED";
    public static final String AUDIT_VALIDATION_FAILED = "VALIDATION_FAILED";

    private ScoreTemplateConstants() {
    }
}
