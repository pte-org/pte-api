package com.pte.itembank.internal.generation.constant;

/** Event names and timings of the live view of a generation job (server-sent events). */
public final class GenerationLiveConstants {

    /** Everything known so far, sent first so a tab opened late catches up before the next token. */
    public static final String EVENT_SNAPSHOT = "snapshot";
    /** An agent started a new call for an item: its text starts again from empty. */
    public static final String EVENT_BEGIN = "begin";
    /** A piece of text an agent just wrote. */
    public static final String EVENT_TOKEN = "token";
    public static final String EVENT_STAGE = "stage";
    public static final String EVENT_MEDIA = "media";
    /** The generator's draft of an item as the question form's fields. */
    public static final String EVENT_DRAFT = "draft";
    /** A reviewer's verdict on an item (its text or its drawn chart). */
    public static final String EVENT_REVIEW = "review";
    public static final String REVIEW_CONTENT = "CONTENT";
    public static final String REVIEW_IMAGE = "IMAGE";
    /**
     * One check inside writing a draft: the prompt put together, the model writing, the JSON read, the fixed rules of
     * the task type and the duplicate check. A new PROMPT check starts a new round and clears the earlier checks.
     */
    public static final String EVENT_CHECK = "check";
    public static final String CHECK_PROMPT = "PROMPT";
    public static final String CHECK_WRITE = "WRITE";
    public static final String CHECK_JSON = "JSON";
    public static final String CHECK_RULES = "RULES";
    public static final String CHECK_DUPLICATE = "DUPLICATE";
    /** The reviewer's steps: its prompt, its call, and the reading of its verdict. */
    public static final String CHECK_REVIEW_PROMPT = "REVIEW_PROMPT";
    public static final String CHECK_REVIEW_WRITE = "REVIEW_WRITE";
    public static final String CHECK_REVIEW_VERDICT = "REVIEW_VERDICT";
    public static final String CHECK_RUNNING = "RUNNING";
    public static final String CHECK_PASSED = "PASSED";
    public static final String CHECK_FAILED = "FAILED";
    /** Facts of a check, shown next to it; the client words them. */
    public static final String FACT_CEFR = "cefr";
    public static final String FACT_TOPIC = "topic";
    public static final String FACT_AVOIDED_TOPICS = "avoidedTopics";
    public static final String FACT_FEEDBACK = "feedback";
    public static final String FACT_RETRY = "retry";
    public static final String FACT_FIXED = "fixed";
    public static final String FACT_ISSUES = "issues";
    /** How a detail line under a check is read (see LiveEvent.CheckDetail). */
    public static final String UNIT_CHARS = "CHARS";
    public static final String UNIT_WORDS = "WORDS";
    public static final String UNIT_ITEMS = "ITEMS";
    public static final String UNIT_TEXT = "TEXT";
    public static final String UNIT_PRESENT = "PRESENT";
    /** Names of the detail lines that are not fields of a draft. */
    public static final String DETAIL_SYSTEM_PROMPT = "systemPrompt";
    public static final String DETAIL_TASK_PROMPT = "taskPrompt";
    public static final String DETAIL_PROMPT_KEY = "promptKey";
    public static final String DETAIL_CEFR = "cefr";
    public static final String DETAIL_TOPIC = "topicHint";
    public static final String DETAIL_AVOIDED_TOPICS = "avoidedTopics";
    public static final String DETAIL_FEEDBACK = "feedback";
    public static final String DETAIL_OUTPUT = "output";
    public static final String DETAIL_HINT_VI = "answerHintVi";
    public static final String DETAIL_HINT_EN = "answerHintEn";
    public static final String DETAIL_CONTENT_HASH = "contentHash";
    public static final String DETAIL_TASK_TYPE = "taskType";
    public static final String DETAIL_WITH_HINTS = "withHints";
    public static final String DETAIL_WITH_FIX = "withFix";
    public static final String DETAIL_CRITERIA = "criteria";
    public static final String DETAIL_CRITERIA_FAILED = "criteriaFailed";
    /** Characters of a content hash shown; the full hash says nothing more to a person. */
    public static final int CONTENT_HASH_SHOWN = 12;
    /** An item ended (saved or failed); the client reloads the job for its outcome. */
    public static final String EVENT_ITEM = "item";
    /** The job ended; the stream closes after it. */
    public static final String EVENT_END = "end";
    public static final String HEARTBEAT_COMMENT = "ping";

    /** A stream lives at most this long; the client opens a new one while the job still runs. */
    public static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;
    /** Keeps proxies from closing a quiet stream while the model is thinking. */
    public static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    /** What a finished job's live state is kept for, so a tab opened just after still shows the drafts. */
    public static final long FINISHED_RETENTION_MS = 10L * 60 * 1000;
    /** A job nobody watches and nothing writes to is dropped after this long (a job that never ran here). */
    public static final long IDLE_RETENTION_MS = 30L * 60 * 1000;

    private GenerationLiveConstants() {
    }
}
