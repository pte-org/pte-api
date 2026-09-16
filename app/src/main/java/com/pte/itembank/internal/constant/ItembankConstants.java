package com.pte.itembank.internal.constant;

/** Centralized codes/labels for itembank. */
public final class ItembankConstants {

    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    public static final String INVALID_QUESTION_FIELDS = "INVALID_QUESTION_FIELDS";
    public static final String UNKNOWN_TASK_TYPE = "UNKNOWN_TASK_TYPE";

    // Task-type-specific validation codes
    public static final String AUDIO_PROMPT_REQUIRED = "AUDIO_PROMPT_REQUIRED";
    public static final String IMAGE_PROMPT_REQUIRED = "IMAGE_PROMPT_REQUIRED";
    public static final String PROMPT_TEXT_REQUIRED = "PROMPT_TEXT_REQUIRED";
    public static final String WORD_COUNT_REQUIRED = "WORD_COUNT_REQUIRED";
    public static final String OPTIONS_REQUIRED = "OPTIONS_REQUIRED";
    public static final String CORRECT_OPTION_REQUIRED = "CORRECT_OPTION_REQUIRED";
    public static final String CORRECT_ANSWER_REQUIRED = "CORRECT_ANSWER_REQUIRED";

    public static final String OPTION_TEXT_REQUIRED = "Option text is required";
    public static final String TASK_TYPE_REQUIRED = "Task type is required";
    public static final String TITLE_REQUIRED = "Title is required";
    public static final String TASK_SKILL_MAPPING_LOAD_FAILED = "Failed to load task-skill mapping from %s";

    private ItembankConstants() {
    }
}
