package com.pte.practice.internal.constant;

import java.util.Set;

/** Stable practice catalog, session and validation constants. */
public final class PracticeConstants {

    public static final String PRACTICE_ENTITLEMENT_PATH = "/api/v1/student/practice/entitlement";
    public static final String PRACTICE_CATALOG_PATH = "/api/v1/student/practice/catalog";
    public static final String PRACTICE_PROGRESS_PATH = "/api/v1/student/practice/progress";
    public static final String PRACTICE_SESSION_PATH = "/api/v1/student/practice/sessions";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String PRACTICE_WEB_ENABLED_PROPERTY = "practice.web.enabled";
    public static final String PRACTICE_STRICT_ENTITLEMENT_ENABLED_PROPERTY =
            "practice.strict-entitlement.enabled";

    public static final String PRACTICE_NOT_ENTITLED = "PRACTICE_NOT_ENTITLED";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REQUIRED = "PRACTICE_IDEMPOTENCY_KEY_REQUIRED";
    public static final String PRACTICE_IDEMPOTENCY_KEY_INVALID = "PRACTICE_IDEMPOTENCY_KEY_INVALID";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REUSED = "PRACTICE_IDEMPOTENCY_KEY_REUSED";
    public static final String PRACTICE_STALE_SESSION_VERSION = "PRACTICE_STALE_SESSION_VERSION";
    public static final String PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
    public static final String PRACTICE_SESSION_NOT_STARTABLE = "PRACTICE_SESSION_NOT_STARTABLE";
    public static final String PRACTICE_SESSION_EXPIRED = "PRACTICE_SESSION_EXPIRED";
    public static final String PRACTICE_UNSUPPORTED_RUNTIME = "PRACTICE_UNSUPPORTED_RUNTIME";
    public static final String PRACTICE_CAPABILITY_MISMATCH = "PRACTICE_CAPABILITY_MISMATCH";
    public static final String PRACTICE_CONTENT_NOT_READY = "PRACTICE_CONTENT_NOT_READY";
    public static final String PRACTICE_CONFIDENCE_REQUIRED = "PRACTICE_CONFIDENCE_REQUIRED";
    public static final String PRACTICE_ANSWER_INVALID = "PRACTICE_ANSWER_INVALID";
    public static final String PRACTICE_ANSWER_PAYLOAD_REQUIRED = "PRACTICE_ANSWER_PAYLOAD_REQUIRED";
    public static final String PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE =
            "PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE";
    public static final String PRACTICE_PRODUCT_NOT_FOUND = "PRACTICE_PRODUCT_NOT_FOUND";
    public static final String PRACTICE_SCORING_RATE_LIMITED = "PRACTICE_SCORING_RATE_LIMITED";
    public static final String PRACTICE_SCORING_RETRY_NOT_ALLOWED = "PRACTICE_SCORING_RETRY_NOT_ALLOWED";
    public static final String PRACTICE_SCORING_EXCHANGE = "pte.practice.scoring";
    public static final String PRACTICE_SCORING_DLX = "pte.practice.scoring.dlx";
    public static final String PRACTICE_SCORING_QUEUE = "pte.practice.scoring.jobs";
    public static final String PRACTICE_SCORING_DLQ = "pte.practice.scoring.jobs.dlq";
    public static final String PRACTICE_SCORING_ROUTING_KEY = "practice.scoring.score";
    public static final String PRACTICE_MEDIA_BINDING_REQUIRED = "PRACTICE_MEDIA_BINDING_REQUIRED";
    public static final String PRACTICE_MEDIA_SESSION_NOT_LIVE = "PRACTICE_MEDIA_SESSION_NOT_LIVE";
    public static final String PRACTICE_MEDIA_ITEM_NOT_RECORDABLE = "PRACTICE_MEDIA_ITEM_NOT_RECORDABLE";

    public static final String PRACTICE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE =
            "Retry this practice action with a stable Idempotency-Key.";
    public static final String PRACTICE_IDEMPOTENCY_KEY_INVALID_MESSAGE =
            "Idempotency-Key must be between 1 and 128 characters.";
    public static final String PRACTICE_IDEMPOTENCY_KEY_REUSED_MESSAGE =
            "This request key was already used for a different practice request.";
    public static final String PRACTICE_STALE_SESSION_VERSION_MESSAGE =
            "This practice session changed in another tab. Reload before continuing.";
    public static final String PRACTICE_SESSION_NOT_STARTABLE_MESSAGE =
            "This practice session is not ready for that action.";
    public static final String PRACTICE_SESSION_EXPIRED_MESSAGE =
            "This practice session has expired.";
    public static final String PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE =
            "This practice task is not available in the current runtime.";
    public static final String PRACTICE_CAPABILITY_MISMATCH_MESSAGE =
            "This device does not support all capabilities required by the selected practice.";
    public static final String PRACTICE_CONTENT_NOT_READY_MESSAGE =
            "This practice content is not ready yet.";
    public static final String PRACTICE_CONFIDENCE_REQUIRED_MESSAGE =
            "Choose a confidence level before submitting an answered practice item.";
    public static final String PRACTICE_ANSWER_INVALID_MESSAGE =
            "This answer does not match the selected practice task.";
    public static final String PRACTICE_ANSWER_PAYLOAD_REQUIRED_MESSAGE =
            "Provide an answer before submitting this practice item.";
    public static final String PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE_MESSAGE =
            "Practice scoring is temporarily unavailable. Please submit this answer again.";
    public static final String PRACTICE_SCORING_RATE_LIMITED_MESSAGE =
            "You are submitting answers too quickly. Wait a moment and try again.";
    public static final String PRACTICE_SCORING_RETRY_NOT_ALLOWED_MESSAGE =
            "This answer cannot be scored again.";
    public static final String PRACTICE_PRODUCT_NOT_FOUND_MESSAGE =
            "The requested practice product is not available.";
    public static final String PRACTICE_MEDIA_BINDING_REQUIRED_MESSAGE =
            "This recording must belong to the active practice task.";
    public static final String PRACTICE_MEDIA_SESSION_NOT_LIVE_MESSAGE =
            "The practice session is no longer accepting recordings.";
    public static final String PRACTICE_MEDIA_ITEM_NOT_RECORDABLE_MESSAGE =
            "This practice task does not accept a recording.";

    public static final String PRACTICE_PRODUCT_CODE = "PTE_CORE_PRACTICE";
    public static final String PRACTICE_PRODUCT_TITLE = "PTE Core Practice";
    public static final String PRACTICE_DEFAULT_DISPLAY_NAME = "Practice";
    public static final String PRACTICE_PERSISTED_CATALOG_PROVENANCE = "PERSISTED_CANONICAL_CATALOG";
    public static final String PRACTICE_RUNTIME_REGISTRY_PROVENANCE = "CANONICAL_RUNTIME_REGISTRY";
    public static final String PRACTICE_RUNTIME_CONTRACT_READY_STATUS = "RUNTIME_CONTRACT_READY";
    public static final String PRACTICE_SERVER_READY_STATUS = "SERVER_READY";
    public static final String PRACTICE_SERVER_NOT_READY_STATUS = "SERVER_NOT_READY";
    public static final String PRACTICE_RUNTIME_ONLY_STATUS = "RUNTIME_ONLY_NO_PERSISTED_CONTENT";
    public static final String PRACTICE_NEXT_ACTION = "NEXT";
    public static final String PRACTICE_UNKNOWN_TASK_CODE = "UNKNOWN";
    public static final String PRACTICE_SHA256_UNAVAILABLE = "SHA-256 is unavailable";
    public static final String PRACTICE_CATALOG_VERSION = "2026.10";
    /** Task codes the exam knows but practice never offers (the exam-only unscored warm-up). */
    public static final Set<String> PRACTICE_HIDDEN_TASK_CODES = Set.of("PERSONAL_INTRODUCTION");
    /** Every exam renderer except the hidden Personal Introduction. */
    public static final Set<String> PRACTICE_CLIENT_SUPPORTED_RENDERER_KEYS = Set.of(
            "READ_ALOUD_V1",
            "REPEAT_SENTENCE_V1",
            "DESCRIBE_IMAGE_V1",
            "RE_TELL_LECTURE_V1",
            "ANSWER_SHORT_QUESTION_V1",
            "RESPOND_TO_A_SITUATION_V1",
            "SUMMARIZE_GROUP_DISCUSSION_V1",
            "SUMMARIZE_WRITTEN_TEXT_V1",
            "WRITE_ESSAY_V1",
            "MC_READING_SINGLE_V1",
            "MC_READING_MULTIPLE_V1",
            "RE_ORDER_PARAGRAPHS_V1",
            "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1",
            "FILL_IN_THE_BLANKS_DROPDOWN_V1",
            "SUMMARIZE_SPOKEN_TEXT_V1",
            "MC_LISTENING_SINGLE_V1",
            "MC_LISTENING_MULTIPLE_V1",
            "FILL_IN_THE_BLANKS_TYPE_IN_V1",
            "HIGHLIGHT_CORRECT_SUMMARY_V1",
            "SELECT_MISSING_WORD_V1",
            "HIGHLIGHT_INCORRECT_WORDS_V1",
            "WRITE_FROM_DICTATION_V1");
    public static final Set<String> PRACTICE_RECORDING_RENDERER_KEYS = Set.of(
            "READ_ALOUD_V1",
            "REPEAT_SENTENCE_V1",
            "DESCRIBE_IMAGE_V1",
            "RE_TELL_LECTURE_V1",
            "ANSWER_SHORT_QUESTION_V1",
            "RESPOND_TO_A_SITUATION_V1",
            "SUMMARIZE_GROUP_DISCUSSION_V1");
    public static final int PRACTICE_TIME_LIMIT_SECONDS = 3_600;
    /** Prompt audio/image links must stay valid for the whole session timer, so the expiry exceeds it. */
    public static final long PRACTICE_PROMPT_MEDIA_TTL_SECONDS = PRACTICE_TIME_LIMIT_SECONDS * 2L;
    public static final int PRACTICE_PROGRESS_MAX_SESSIONS = 100;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    public static final int MAX_ANSWER_PAYLOAD_LENGTH = 16_384;

    public static final int ENTITLEMENT_REFRESH_SECONDS = 300;

    private PracticeConstants() {
    }
}
