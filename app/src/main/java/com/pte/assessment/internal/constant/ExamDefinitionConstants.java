package com.pte.assessment.internal.constant;

/** Centralized codes/labels for exam definitions. */
public final class ExamDefinitionConstants {

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
    public static final String TEMPLATE_SCORING_PROFILE_INVALID =
            "Runtime scoring profile does not match the task type: ";
    public static final String RUNTIME_PROFILE_NOT_PINNED = "RUNTIME_PROFILE_NOT_PINNED";
    public static final String RUNTIME_PROFILE_INVALID = "RUNTIME_PROFILE_INVALID";
    public static final String UNKNOWN_TASK_TYPE = "UNKNOWN_TASK_TYPE";
    public static final String MISSING_TASK_TYPE_KEY = "missing task type key";
    public static final String UNKNOWN_TEMPLATE_POLICY = "Unknown template policy: ";
    public static final String PLATFORM_TEMPLATE_POLICY_NOT_CUSTOM =
            "A platform template must cover the whole task-type catalog, so it cannot use the CUSTOM policy";
    public static final String TEMPLATE_FEASIBILITY_DEPENDENCIES_NOT_CONFIGURED =
            "Template feasibility dependencies are not configured";
    public static final String TEMPLATE_READ_SERVICE_NOT_CONFIGURED = "Score template read service is not configured";
    public static final String ACADEMIC_DRAFT_WRITE_REQUIRED =
            "Only academic staff, academic managers, or platform admins may manage academic drafts";
    public static final String ACADEMIC_REVIEW_REQUIRED =
            "Only an academic manager may review another author's score template";
    public static final String ACADEMIC_PUBLISH_REQUIRED =
            "Only an academic manager may activate another author's score template";
    public static final String ACADEMIC_SELF_APPROVAL_FORBIDDEN =
            "An academic manager cannot approve or activate their own score template";

    public static final String NAME_REQUIRED = "Template name is required";
    public static final String ITEMS_REQUIRED = "A template needs at least one item";
    public static final String MISSING_TASK_TYPES = "Missing required task types: ";
    public static final String INVALID_COUNT_RANGE = "minCount/maxCount invalid for task type ";
    public static final String NEGATIVE_WEIGHT = "Weight cannot be negative for task type ";
    public static final String SKILL_WITH_NO_WEIGHT = "No task type contributes any weight to skill ";
    public static final String SKILL_WEIGHT_NOT_100 = "Skill weight column must total exactly 100 for ";
    public static final String MISSING_CATALOG_TASK_TYPES =
            "A default template must contain every active task type. Missing: ";
    public static final String HOST_TASK_TYPE_OUTSIDE_BASE =
            "Task type is not part of the default template this template was created from: ";
    public static final String HOST_TEMPLATE_NO_BASE =
            "The default template this template was created from is no longer available";
    public static final String HOST_TEMPLATE_NOT_ACTIVE = "Only an active template can be retired";
    public static final String HOST_TEMPLATE_TENANT_REQUIRED = "Only a tenant host can manage its own score templates";
    public static final String DUPLICATE_TASK_TYPES = "Duplicate task types: ";
    public static final String DUPLICATE_SEQUENCES = "Duplicate template sequence values: ";
    public static final String INVALID_SEQUENCE = "Template sequence must be non-negative: ";
    public static final String TEMPLATE_VALIDATION_FAILED = "SCORE_TEMPLATE_VALIDATION_FAILED";
    public static final String TEMPLATE_RUNTIME_NOT_READY = "TEMPLATE_RUNTIME_NOT_READY";
    public static final String TEMPLATE_STANDARD_TASK_TYPES_MISSING = "TEMPLATE_STANDARD_TASK_TYPES_MISSING";
    public static final String TEMPLATE_NO_SCORED_TASKS = "TEMPLATE_NO_SCORED_TASKS";
    public static final String TEMPLATE_QUESTION_BANK_INSUFFICIENT =
            "Not enough approved questions are available for task type: ";
    public static final String TEMPLATE_UNSCORED_WEIGHT = "Unscored task must have zero weight: ";
    public static final String TEMPLATE_WEIGHT_OUT_OF_RANGE = "Weight must be between 0 and 100: ";
    public static final String TEMPLATE_WEIGHT_PRECISION = "Weight can have at most two decimal places: ";
    public static final String TEMPLATE_RUNTIME_NOT_READY_MESSAGE =
            "This template is saved as a draft, but it cannot be activated yet.";

    public static final String AUDIT_AGGREGATE_TYPE = "SCORE_TEMPLATE";
    public static final String AUDIT_CREATED = "CREATED";
    public static final String AUDIT_UPDATED = "UPDATED";
    public static final String AUDIT_CLONED = "CLONED";
    public static final String AUDIT_SUBMITTED = "SUBMITTED_FOR_APPROVAL";
    public static final String AUDIT_APPROVED = "APPROVED_FOR_ACTIVATION";
    public static final String AUDIT_REJECTED = "REJECTED_TO_DRAFT";
    public static final String AUDIT_DELETED = "DELETED_DRAFT";
    public static final String AUDIT_ACTIVATED = "ACTIVATED";
    public static final String AUDIT_RETIRED = "RETIRED";
    public static final String AUDIT_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String AUDIT_PUBLICATION_USAGE_RECORDED = "TASK_TYPE_PUBLICATION_USAGE_RECORDED";

    private ExamDefinitionConstants() {
    }
}
