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
