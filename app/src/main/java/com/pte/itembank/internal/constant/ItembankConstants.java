package com.pte.itembank.internal.constant;

/** Centralized codes/labels for itembank. */
public final class ItembankConstants {

    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    public static final String INVALID_QUESTION_FIELDS = "INVALID_QUESTION_FIELDS";
    public static final String INVALID_QUESTION_TYPE = "INVALID_QUESTION_TYPE";
    public static final String UNKNOWN_TASK_TYPE = "UNKNOWN_TASK_TYPE";
    public static final String TASK_TYPE_KEY_INVALID = "TASK_TYPE_KEY_INVALID";
    public static final String TASK_TYPE_KEY_ALREADY_USED = "TASK_TYPE_KEY_ALREADY_USED";
    public static final String TASK_TYPE_DISPLAY_NAME_ALREADY_USED = "TASK_TYPE_DISPLAY_NAME_ALREADY_USED";
    public static final String TASK_TYPE_DISPLAY_NAME_INVALID = "TASK_TYPE_DISPLAY_NAME_INVALID";
    public static final String TASK_TYPE_CAPABILITY_NOT_FOUND = "TASK_TYPE_CAPABILITY_NOT_FOUND";
    public static final String TASK_TYPE_RUNTIME_LOCKED = "TASK_TYPE_RUNTIME_LOCKED";
    public static final String TASK_TYPE_CUSTOM_CREATION_DISABLED = "TASK_TYPE_CUSTOM_CREATION_DISABLED";
    public static final String UNSUPPORTED_RUNTIME_CONTRACT = "UNSUPPORTED_RUNTIME_CONTRACT";

    public static final String TASK_TYPE_KEY_INVALID_MESSAGE =
            "Use a key with letters, numbers, and underscores, beginning with a letter.";
    public static final String TASK_TYPE_KEY_ALREADY_USED_MESSAGE =
            "That task type key is already in use.";
    public static final String TASK_TYPE_DISPLAY_NAME_ALREADY_USED_MESSAGE =
            "That display name is already used. Choose a different name.";
    public static final String TASK_TYPE_DISPLAY_NAME_INVALID_MESSAGE =
            "Enter a display name between 1 and 128 characters.";
    public static final String TASK_TYPE_CAPABILITY_NOT_FOUND_MESSAGE =
            "The selected task screen is no longer available for new configuration.";
    public static final String TASK_TYPE_RUNTIME_LOCKED_MESSAGE =
            "This task type is used by a published template, so its runtime contract is locked.";
    public static final String TASK_TYPE_CUSTOM_CREATION_DISABLED_MESSAGE =
            "Custom task types are temporarily unavailable while the platform rollout is being verified.";

    public static final String TASK_TYPE_AUDIT_AGGREGATE = "TASK_TYPE";
    public static final String TASK_TYPE_CREATED = "CREATED";
    public static final String TASK_TYPE_UPDATED = "UPDATED";
    public static final String TASK_TYPE_RUNTIME_UPDATED = "RUNTIME_CONTRACT_UPDATED";
    public static final String TASK_TYPE_RETIRED = "RETIRED";
    public static final String TASK_TYPE_DUPLICATE_REJECTED = "DUPLICATE_REJECTED";
    public static final String TASK_TYPE_RUNTIME_LOCK_CONFLICT = "RUNTIME_LOCK_CONFLICT";
    public static final String TASK_TYPE_LEGACY_ADAPTER_USED = "LEGACY_ADAPTER_USED";

    // Task-type-specific validation codes
    public static final String AUDIO_PROMPT_REQUIRED = "AUDIO_PROMPT_REQUIRED";
    public static final String IMAGE_PROMPT_REQUIRED = "IMAGE_PROMPT_REQUIRED";
    public static final String PROMPT_TEXT_REQUIRED = "PROMPT_TEXT_REQUIRED";
    public static final String WORD_COUNT_REQUIRED = "WORD_COUNT_REQUIRED";
    public static final String OPTIONS_REQUIRED = "OPTIONS_REQUIRED";
    public static final String CORRECT_OPTION_REQUIRED = "CORRECT_OPTION_REQUIRED";
    public static final String CORRECT_ANSWER_REQUIRED = "CORRECT_ANSWER_REQUIRED";
    public static final String INVALID_QUESTION_STATUS_TRANSITION = "INVALID_QUESTION_STATUS_TRANSITION";
    public static final String QUESTION_REJECTION_REASON_REQUIRED = "Question rejection reason is required";
    public static final String QUESTION_VERSION_CONFLICT = "QUESTION_VERSION_CONFLICT";

    public static final String OPTION_TEXT_REQUIRED = "Option text is required";
    public static final String TASK_TYPE_REQUIRED = "Task type is required";
    public static final String TITLE_REQUIRED = "Title is required";
    public static final String TASK_SKILL_MAPPING_LOAD_FAILED = "Failed to load task-skill mapping from %s";

    private ItembankConstants() {
    }
}
