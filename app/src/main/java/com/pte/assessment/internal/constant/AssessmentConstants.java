package com.pte.assessment.internal.constant;

/** Centralized codes/labels for assessment. */
public final class AssessmentConstants {

    public static final String BLUEPRINT_NOT_FOUND = "BLUEPRINT_NOT_FOUND";
    public static final String EMPTY_BLUEPRINT = "EMPTY_BLUEPRINT";
    public static final String INVALID_SECTION = "INVALID_SECTION";
    public static final String INSUFFICIENT_QUESTION_BANK = "INSUFFICIENT_QUESTION_BANK";
    public static final String INVALID_SKILL_SELECTION = "INVALID_SKILL_SELECTION";
    public static final String TEMPLATE_NOT_ACTIVE = "TEMPLATE_NOT_ACTIVE";
    public static final String DETERMINISTIC_ALGORITHM_VERSION = "PTE_SEEDED_V1";

    public static final String QUESTION_REFERENCE_REQUIRED = "Question reference is required";
    public static final String SECTION_REQUIRED = "Section is required";
    public static final String BLUEPRINT_NAME_REQUIRED = "Blueprint name is required";
    public static final String BLUEPRINT_ITEMS_REQUIRED = "A blueprint needs at least one item";
    public static final String BLUEPRINT_REJECTION_REASON_REQUIRED = "A rejection reason is required";
    public static final String BLUEPRINT_STATUS_INVALID = "Blueprint status does not allow this operation";
    public static final String BLUEPRINT_ITEM_INVALID = "Blueprint contains an invalid or unavailable question";
    public static final String BLUEPRINT_DUPLICATE_QUESTION = "A question cannot appear more than once in a blueprint";
    public static final String BLUEPRINT_TEMPLATE_COMPLIANCE_INVALID = "Blueprint does not match the active PTE template counts";
    public static final String SNAPSHOT_OPTIONS_SERIALIZATION_FAILED = "Failed to serialize snapshot options";

    private AssessmentConstants() {
    }
}
