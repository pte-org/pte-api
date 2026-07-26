package com.pte.authoring.constant;

/** Centralized codes/labels for authoring. */
public final class AuthoringConstants {

    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    public static final String BLUEPRINT_NOT_FOUND = "BLUEPRINT_NOT_FOUND";
    public static final String SHARED_WRITE_FORBIDDEN = "SHARED_WRITE_FORBIDDEN";
    public static final String CROSS_TENANT_ACCESS = "CROSS_TENANT_ACCESS";
    public static final String INVALID_QUESTION_FIELDS = "INVALID_QUESTION_FIELDS";
    public static final String EMPTY_BLUEPRINT = "EMPTY_BLUEPRINT";
    public static final String UNKNOWN_TASK_TYPE = "UNKNOWN_TASK_TYPE";

    // Task-type-specific validation codes
    public static final String AUDIO_PROMPT_REQUIRED = "AUDIO_PROMPT_REQUIRED";
    public static final String IMAGE_PROMPT_REQUIRED = "IMAGE_PROMPT_REQUIRED";
    public static final String PROMPT_TEXT_REQUIRED = "PROMPT_TEXT_REQUIRED";
    public static final String WORD_COUNT_REQUIRED = "WORD_COUNT_REQUIRED";
    public static final String OPTIONS_REQUIRED = "OPTIONS_REQUIRED";
    public static final String CORRECT_OPTION_REQUIRED = "CORRECT_OPTION_REQUIRED";
    public static final String CORRECT_ANSWER_REQUIRED = "CORRECT_ANSWER_REQUIRED";
    public static final String PRIVATE_REQUIRES_TENANT = "PRIVATE_REQUIRES_TENANT";

    public static final String AGGREGATE_SNAPSHOT = "ExamSnapshot";
    public static final String EVENT_SNAPSHOT_PUBLISHED = "ExamSnapshotPublished";

    private AuthoringConstants() {
    }
}
