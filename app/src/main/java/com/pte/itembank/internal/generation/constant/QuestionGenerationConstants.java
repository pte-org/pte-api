package com.pte.itembank.internal.generation.constant;

/** Centralized codes, messages and limits for AI question generation. */
public final class QuestionGenerationConstants {

    public static final String GENERATION_DISABLED = "GENERATION_DISABLED";
    public static final String GENERATION_TASK_TYPE_UNSUPPORTED = "GENERATION_TASK_TYPE_UNSUPPORTED";
    public static final String GENERATION_COUNT_OUT_OF_RANGE = "GENERATION_COUNT_OUT_OF_RANGE";
    public static final String GENERATION_CEFR_INVALID = "GENERATION_CEFR_INVALID";
    public static final String GENERATION_POOL_INVALID = "GENERATION_POOL_INVALID";
    public static final String GENERATION_TOPIC_HINT_INVALID = "GENERATION_TOPIC_HINT_INVALID";
    public static final String GENERATION_DAILY_LIMIT_REACHED = "GENERATION_DAILY_LIMIT_REACHED";
    public static final String GENERATION_GLOBAL_LIMIT_REACHED = "GENERATION_GLOBAL_LIMIT_REACHED";
    public static final String GENERATION_TOO_MANY_JOBS = "GENERATION_TOO_MANY_JOBS";
    public static final String GENERATION_JOB_ALREADY_RUNNING = "GENERATION_JOB_ALREADY_RUNNING";
    public static final String GENERATION_JOB_NOT_FOUND = "GENERATION_JOB_NOT_FOUND";
    public static final String GENERATION_JOB_NOT_CANCELLABLE = "GENERATION_JOB_NOT_CANCELLABLE";

    public static final String GENERATION_DISABLED_MESSAGE = "AI question generation is turned off.";
    public static final String GENERATION_TASK_TYPE_UNSUPPORTED_MESSAGE =
            "AI generation does not support this task type yet.";
    public static final String GENERATION_COUNT_OUT_OF_RANGE_MESSAGE =
            "Choose between 1 and the maximum number of questions per job.";
    public static final String GENERATION_CEFR_INVALID_MESSAGE = "Choose a valid CEFR level.";
    public static final String GENERATION_POOL_INVALID_MESSAGE = "Choose the practice or the exam pool.";
    public static final String GENERATION_TOPIC_HINT_INVALID_MESSAGE =
            "The topic may only contain letters, numbers, spaces and basic punctuation (up to 200 characters).";
    public static final String GENERATION_DAILY_LIMIT_REACHED_MESSAGE = "You reached the daily generation limit.";
    public static final String GENERATION_GLOBAL_LIMIT_REACHED_MESSAGE =
            "The platform reached the daily generation limit.";
    public static final String GENERATION_TOO_MANY_JOBS_MESSAGE =
            "Too many generation jobs are running. Try again in a few minutes.";
    public static final String GENERATION_JOB_ALREADY_RUNNING_MESSAGE =
            "You already have a generation job in progress.";
    public static final String GENERATION_JOB_NOT_FOUND_MESSAGE = "Generation job not found.";
    public static final String GENERATION_JOB_NOT_CANCELLABLE_MESSAGE = "This generation job already finished.";

    /** Provider failure codes (also stored as item failure codes). */
    public static final String GENERATION_PROVIDER_AUTH = "GENERATION_PROVIDER_AUTH";
    public static final String GENERATION_PROVIDER_UNAVAILABLE = "GENERATION_PROVIDER_UNAVAILABLE";
    public static final String GENERATION_PROVIDER_REJECTED = "GENERATION_PROVIDER_REJECTED";
    public static final String GENERATION_EMPTY_RESPONSE = "GENERATION_EMPTY_RESPONSE";

    /** Item failure codes. */
    public static final String GENERATION_ITEMBANK_REJECTED = "GENERATION_ITEMBANK_REJECTED";
    public static final String GENERATION_DUPLICATE_CONTENT = "GENERATION_DUPLICATE_CONTENT";
    public static final String GENERATION_CALL_BUDGET_EXCEEDED = "GENERATION_CALL_BUDGET_EXCEEDED";
    public static final String GENERATION_SCHEMA_INVALID = "GENERATION_SCHEMA_INVALID";
    public static final String GENERATION_VALIDATION_FAILED = "GENERATION_VALIDATION_FAILED";
    public static final String GENERATION_REVIEW_FAILED = "GENERATION_REVIEW_FAILED";
    public static final String GENERATION_REVIEW_PARSING_FAILED = "GENERATION_REVIEW_PARSING_FAILED";
    public static final String GENERATION_NO_GENERATOR = "GENERATION_NO_GENERATOR";
    public static final String GENERATION_INTERNAL_ERROR = "GENERATION_INTERNAL_ERROR";
    public static final String GENERATION_REQUESTER_NOT_ALLOWED = "GENERATION_REQUESTER_NOT_ALLOWED";
    public static final String GENERATION_AUDIO_DURATION_OUT_OF_RANGE = "GENERATION_AUDIO_DURATION_OUT_OF_RANGE";
    public static final String GENERATION_MEDIA_FAILED = "GENERATION_MEDIA_FAILED";
    public static final String GENERATION_AI_PROVIDER_UNAVAILABLE = "GENERATION_AI_PROVIDER_UNAVAILABLE";
    public static final String GENERATION_AI_PROVIDER_FAILED = "GENERATION_AI_PROVIDER_FAILED";

    /** Startup validation messages: never include configured values. */
    public static final String CONFIG_STUB_PROFILE_ONLY =
            "itembank.generation.stub may only be enabled with the test, local or local-schema profile.";
    public static final String CONFIG_LLM_BASE_URL_REQUIRED = "itembank.generation.llm.base-url is required.";
    public static final String CONFIG_LLM_BASE_URL_INVALID =
            "itembank.generation.llm.base-url must be an https URL (http only for local profiles).";
    public static final String CONFIG_LLM_API_KEY_REQUIRED = "itembank.generation.llm.api-key is required.";
    public static final String CONFIG_LLM_MODEL_REQUIRED =
            "itembank.generation.llm generator and reviewer models are required.";

    public static final int MAX_ITEMS_PER_JOB_HARD_LIMIT = 10;
    public static final int MAX_TOPIC_HINT_LENGTH = 500;
    public static final int MAX_FAILURE_NOTE_LENGTH = 300;
    public static final int MAX_PAGE_SIZE = 50;
    public static final long SUBMIT_ADVISORY_LOCK_KEY = 7_340_034L;
    public static final int META_SCHEMA_VERSION = 1;
    public static final String PROMPT_VERSION = "2026-11-07";
    public static final int ESTIMATED_SECONDS_TEXT = 45;
    public static final int ESTIMATED_SECONDS_MEDIA = 90;
    public static final String EDITED_BY_HUMAN = "editedByHuman";
    public static final String WORKER_ID_PREFIX = "gen-";
    public static final String EXECUTOR_NAME = "questionGenerationExecutor";
    public static final String EXECUTOR_THREAD_PREFIX = "question-gen-";
    public static final String STUB_GENERATOR_NAME = "stub";
    public static final String STUB_TASK_TYPE = "MC_READING_SINGLE";

    public static final String PROMPT_CATEGORY_GENERATION = "GENERATION";
    public static final String PROMPT_CATEGORY_REVIEW = "REVIEW";
    public static final String PROMPT_CATEGORY_SCORING = "SCORING";
    public static final String PROMPT_CATEGORY_SHARED = "SHARED";
    public static final String PROMPT_KEY_COMMON_GENERATOR = "COMMON_GENERATOR";
    public static final String PROMPT_KEY_REVIEWER = "REVIEWER";
    /** The reviewer that looks at a drawn chart (a second REVIEWER prompt). */
    public static final String PROMPT_KEY_IMAGE_REVIEWER = "CHART_IMAGE_REVIEWER";
    public static final String PROMPT_KEY_PICTURE_REVIEWER = "PICTURE_IMAGE_REVIEWER";
    /** The writer prompt of a Describe Image question whose image is painted, not drawn from data. */
    public static final String PROMPT_KEY_DESCRIBE_IMAGE_PICTURE = "DESCRIBE_IMAGE_PICTURE";
    public static final String GENERATION_IMAGE_FAILED = "GENERATION_IMAGE_FAILED";
    public static final String GENERATION_IMAGE_KIND_INVALID = "GENERATION_IMAGE_KIND_INVALID";
    public static final String GENERATION_IMAGE_KIND_INVALID_MESSAGE = "Image kind must be AUTO, CHART or PICTURE.";
    public static final String PICTURE_ATTEMPTS_INVALID = "maxImageAttempts must be a whole number from 1 to 5.";
    public static final String PICTURE_FALLBACK_INVALID = "whenImageFails must be ASK or CHART.";
    public static final String IMAGE_FALLBACK_NOTE = "The AI picture could not be made; switched to a chart. ";
    public static final String GENERATION_IMAGE_REVIEW_FAILED = "GENERATION_IMAGE_REVIEW_FAILED";
    /** What the writer is told when a draft is sent back for something other than the content reviewer's notes. */
    public static final String FEEDBACK_INVALID =
            "Your previous answer could not be used. Fix this and return the complete JSON again:";
    public static final String FEEDBACK_DUPLICATE =
            "This exact content already exists in the bank. Write a different item on another angle of the topic.";
    public static final String FEEDBACK_AUDIO_LENGTH =
            "The spoken text gave audio outside the allowed length for this task type. Adjust its length:";
    public static final String FEEDBACK_IMAGE =
            "The chart drawn from your data was rejected by the image reviewer. Keep category labels short (at most "
                    + "two or three words) and fix these issues:";
    public static final String PROMPT_SECTION_INTRO_ID = "intro";
    public static final String PROMPT_SECTION_INTRO_TITLE = "Overview";
    public static final String PROMPT_SECTION_PLACEHOLDERS_ID = "placeholders";
    public static final String PROMPT_SECTION_PLACEHOLDERS_TITLE = "Placeholders";
    public static final String ENGINE_LLM = "LLM";
    public static final String ENGINE_TTS = "TTS";
    public static final String ENGINE_CHART = "CHART";
    public static final String PROMPT_SEED_PATTERN = "classpath*:question-generation/prompt-seeds/*.json";
    public static final String PROMPT_CODE_LANGUAGE = "json";
    public static final String PROMPT_CONTENT_UNREADABLE = "A stored prompt could not be read as JSON.";
    public static final String PROMPT_NOT_FOUND = "AI_PROMPT_NOT_FOUND";
    public static final String PROMPT_NOT_FOUND_MESSAGE = "Prompt not found.";

    private QuestionGenerationConstants() {
    }
}
