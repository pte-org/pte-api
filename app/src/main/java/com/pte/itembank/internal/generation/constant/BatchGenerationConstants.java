package com.pte.itembank.internal.generation.constant;

import java.util.Set;

/** Codes, messages, audit actions, result strings and limits of batch question generation. */
public final class BatchGenerationConstants {

    public static final int DEFAULT_MAX_ITEMS_PER_BATCH = 50;
    /** Mirrors the {@code total_requested} CHECK of V53 and the platform setting maximum. */
    public static final int MAX_ITEMS_PER_BATCH_HARD_LIMIT = 500;
    /** One row per task type and CEFR level: room for every type at all six levels; the item cap bounds the cost. */
    public static final int MAX_ROWS_PER_BATCH = 150;
    public static final int MAX_TOPIC_HINTS_PER_ROW = 10;
    public static final int MAX_BULK_DELETE_ITEMS = 200;
    public static final String RECONCILE_DELAY_PROPERTY = "${itembank.generation.batch.reconcile-delay-ms:300000}";

    public static final String GENERATION_BATCH_DISABLED = "GENERATION_BATCH_DISABLED";
    public static final String GENERATION_BATCH_SIZE_OUT_OF_RANGE = "GENERATION_BATCH_SIZE_OUT_OF_RANGE";
    public static final String GENERATION_BATCH_ROWS_OUT_OF_RANGE = "GENERATION_BATCH_ROWS_OUT_OF_RANGE";
    public static final String GENERATION_BATCH_TOPIC_HINTS_OUT_OF_RANGE = "GENERATION_BATCH_TOPIC_HINTS_OUT_OF_RANGE";
    public static final String GENERATION_PICTURE_DISABLED = "GENERATION_PICTURE_DISABLED";
    public static final String GENERATION_BATCH_NOT_FOUND = "GENERATION_BATCH_NOT_FOUND";
    public static final String GENERATION_BATCH_NOT_CANCELLABLE = "GENERATION_BATCH_NOT_CANCELLABLE";
    public static final String GENERATION_JOB_PART_OF_BATCH = "GENERATION_JOB_PART_OF_BATCH";

    public static final String GENERATION_BATCH_DISABLED_MESSAGE = "Batch question generation is turned off.";
    public static final String GENERATION_BATCH_SIZE_OUT_OF_RANGE_MESSAGE =
            "Each row needs at least one question and the batch may not exceed the maximum number of questions.";
    public static final String GENERATION_BATCH_ROWS_OUT_OF_RANGE_MESSAGE = "A batch has between 1 and 150 rows.";
    public static final String GENERATION_BATCH_TOPIC_HINTS_OUT_OF_RANGE_MESSAGE = "A row may have up to 10 topics.";
    public static final String GENERATION_PICTURE_DISABLED_MESSAGE =
            "AI-painted pictures are turned off. Choose a chart instead.";
    public static final String GENERATION_BATCH_NOT_FOUND_MESSAGE = "Generation batch not found.";
    public static final String GENERATION_BATCH_NOT_CANCELLABLE_MESSAGE = "This generation batch already finished.";
    public static final String GENERATION_JOB_PART_OF_BATCH_MESSAGE =
            "This job belongs to a batch. Cancel the batch instead.";

    /**
     * A child that failed with one of these codes stops the whole batch: every later child would fail the same way
     * (bad credentials, provider down, requester no longer allowed).
     */
    public static final Set<String> FATAL_FAILURE_CODES = Set.of(
            QuestionGenerationConstants.GENERATION_PROVIDER_AUTH,
            QuestionGenerationConstants.GENERATION_PROVIDER_UNAVAILABLE,
            QuestionGenerationConstants.GENERATION_REQUESTER_NOT_ALLOWED);

    public static final String BATCH_AGGREGATE = "QUESTION_GENERATION_BATCH";
    public static final String BATCH_CREATED = "QUESTION_GENERATION_BATCH_CREATED";
    public static final String BATCH_CANCELLED = "QUESTION_GENERATION_BATCH_CANCELLED";
    public static final String BATCH_DRAFTS_DELETED = "QUESTION_GENERATION_BATCH_DRAFTS_DELETED";
    public static final String BATCH_CREATED_SUMMARY = "rows=%d,items=%d,children=%d";
    public static final String BATCH_CANCELLED_SUMMARY = "status=%s";
    public static final String BATCH_DRAFTS_DELETED_SUMMARY = "processed=%d,deleted=%d,remaining=%d";

    /** Per-question results of a bulk draft delete. */
    public static final String RESULT_DELETED = "deleted";
    public static final String RESULT_SKIPPED_NOT_DRAFT = "skipped_not_draft";
    public static final String RESULT_FORBIDDEN = "forbidden";
    public static final String RESULT_NOT_FOUND = "not_found";
    public static final String RESULT_NOT_IN_BATCH = "not_in_batch";

    public static final String HOOK_FAILURE_METRIC = "generation.batch.hook.failures";
    public static final String MAX_ROWS_MESSAGE = "rows must hold 1 to 150 entries";
    public static final String MAX_HINTS_MESSAGE = "topicHints may hold up to 10 entries";
    public static final String MAX_BULK_MESSAGE = "questionPublicIds may hold up to 200 entries";

    private BatchGenerationConstants() {
    }
}
