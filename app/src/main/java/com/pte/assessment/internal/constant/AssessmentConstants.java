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
    public static final String BLUEPRINT_APPROVAL_PLATFORM_ADMIN_REQUIRED =
            "Only platform admins may approve blueprints";
    public static final String BLUEPRINT_REJECTION_PLATFORM_ADMIN_REQUIRED =
            "Only platform admins may reject blueprints";
    public static final String BLUEPRINT_AUTHOR_PLATFORM_AUTHOR_REQUIRED =
            "Only platform authors may manage blueprints";
    public static final String SNAPSHOT_OPTIONS_SERIALIZATION_FAILED = "Failed to serialize snapshot options";
    public static final String PINNED_QUESTION_OPTIONS_DECODE_FAILED = "Pinned question options could not be decoded";
    public static final String EXAMINER_PROMPT_DEPENDENCIES_NOT_CONFIGURED =
            "Examiner prompt dependencies are not configured";
    public static final String TEMPLATE_FEASIBILITY_DEPENDENCIES_NOT_CONFIGURED =
            "Template feasibility dependencies are not configured";
    public static final String SNAPSHOT_RUNTIME_CONTRACT_INVALID = "EXAM_CONFIGURATION_NOT_COMPATIBLE";
    public static final String SNAPSHOT_RUNTIME_CONTRACT_MESSAGE =
            "This exam is not currently compatible with the configured task runtime. Please ask an administrator to review the exam configuration.";
    public static final String AUDIT_AGGREGATE_SNAPSHOT = "EXAM_SNAPSHOT";
    public static final String AUDIT_RUNTIME_MAPPING_REJECTED = "RUNTIME_MAPPING_REJECTED";

    private AssessmentConstants() {
    }
}
