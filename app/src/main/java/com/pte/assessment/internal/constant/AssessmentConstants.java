package com.pte.assessment.internal.constant;

/** Centralized codes/labels for assessment. */
public final class AssessmentConstants {

    public static final String BLUEPRINT_NOT_FOUND = "BLUEPRINT_NOT_FOUND";
    public static final String EMPTY_BLUEPRINT = "EMPTY_BLUEPRINT";
    public static final String INVALID_SECTION = "INVALID_SECTION";

    public static final String QUESTION_REFERENCE_REQUIRED = "Question reference is required";
    public static final String SECTION_REQUIRED = "Section is required";
    public static final String BLUEPRINT_NAME_REQUIRED = "Blueprint name is required";
    public static final String BLUEPRINT_ITEMS_REQUIRED = "A blueprint needs at least one item";
    public static final String SNAPSHOT_OPTIONS_SERIALIZATION_FAILED = "Failed to serialize snapshot options";

    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    public static final String TEMPLATE_NAME_REQUIRED = "Template name is required";
    public static final String TEMPLATE_DESCRIPTION_MAX = "Template description must be at most 255 characters";
    public static final String TEMPLATE_SECTION_REQUIRED = "Template section is required";
    public static final String TEMPLATE_SECTION_WEIGHT_REQUIRED = "Template section weight is required";
    public static final String TEMPLATE_SLOT_TASK_TYPE_REQUIRED = "Template slot task type is required";
    public static final String TEMPLATE_SLOT_QUESTION_COUNT_REQUIRED = "Template slot question count is required";
    public static final String TEMPLATE_SECTION_INVALID = "TEMPLATE_SECTION_INVALID: section=%s";
    public static final String TEMPLATE_TASK_TYPE_INVALID = "TEMPLATE_TASK_TYPE_INVALID: taskType=%s";
    public static final String TEMPLATE_WEIGHT_TOTAL_INVALID =
            "TEMPLATE_WEIGHT_TOTAL_INVALID: expected=100, actual=%d";
    public static final String TEMPLATE_SECTION_WEIGHT_INVALID =
            "TEMPLATE_SECTION_WEIGHT_INVALID: section=%s, weightPercent=%d";
    public static final String TEMPLATE_SECTION_SLOTS_REQUIRED =
            "TEMPLATE_SECTION_SLOTS_REQUIRED: section=%s";
    public static final String TEMPLATE_SLOT_SECTION_MISMATCH =
            "TEMPLATE_SLOT_SECTION_MISMATCH: taskType=%s, expectedSection=%s, actualSection=%s";
    public static final String TEMPLATE_SLOT_QUESTION_COUNT_INVALID =
            "TEMPLATE_SLOT_QUESTION_COUNT_INVALID: taskType=%s, questionCount=%d";
    public static final String TEMPLATE_ACTIVE_STRUCTURE_LOCKED = "TEMPLATE_ACTIVE_STRUCTURE_LOCKED";
    public static final String TEMPLATE_MUST_BE_DRAFT_TO_ACTIVATE = "TEMPLATE_MUST_BE_DRAFT_TO_ACTIVATE";
    public static final String TEMPLATE_ALREADY_ARCHIVED = "TEMPLATE_ALREADY_ARCHIVED";

    private AssessmentConstants() {
    }
}
