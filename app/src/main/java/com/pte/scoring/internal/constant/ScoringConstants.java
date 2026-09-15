package com.pte.scoring.internal.constant;

/** Centralized codes/labels for scoring. */
public final class ScoringConstants {

    // Objective task types: rule-based, synchronous, no vendor call.
    public static final String TASK_TYPE_MC_READING_SINGLE = "MC_READING_SINGLE";
    public static final String TASK_TYPE_MC_READING_MULTIPLE = "MC_READING_MULTIPLE";
    public static final String TASK_TYPE_RE_ORDER_PARAGRAPHS = "RE_ORDER_PARAGRAPHS";
    public static final String TASK_TYPE_FILL_BLANKS_READING = "FILL_BLANKS_READING";
    public static final String TASK_TYPE_FILL_BLANKS_READING_WRITING = "FILL_BLANKS_READING_WRITING";

    // Listening payload-shape discriminators.
    public static final String TASK_TYPE_MC_LISTENING_SINGLE = "MC_LISTENING_SINGLE";
    public static final String TASK_TYPE_MC_LISTENING_MULTIPLE = "MC_LISTENING_MULTIPLE";
    public static final String TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY = "HIGHLIGHT_CORRECT_SUMMARY";
    public static final String TASK_TYPE_SELECT_MISSING_WORD = "SELECT_MISSING_WORD";
    public static final String TASK_TYPE_FILL_BLANKS_LISTENING = "FILL_BLANKS_LISTENING";
    public static final String TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS = "HIGHLIGHT_INCORRECT_WORDS";
    public static final String TASK_TYPE_WRITE_FROM_DICTATION = "WRITE_FROM_DICTATION";

    // AI-scorable task types: routed to the RabbitMQ vendor work queue.
    public static final String TASK_TYPE_READ_ALOUD = "READ_ALOUD";
    public static final String TASK_TYPE_SUMMARIZE_WRITTEN_TEXT = "SUMMARIZE_WRITTEN_TEXT";
    public static final String TASK_TYPE_SUMMARIZE_SPOKEN_TEXT = "SUMMARIZE_SPOKEN_TEXT";
    public static final String TASK_TYPE_WRITE_ESSAY = "WRITE_ESSAY";

    // Speaking task types. Personal Introduction is unscored and intentionally
    // excluded from the AI route; the other seven are catalogued by
    // AiScoringTaskCatalog.
    public static final String TASK_TYPE_PERSONAL_INTRODUCTION = "PERSONAL_INTRODUCTION";
    public static final String TASK_TYPE_REPEAT_SENTENCE = "REPEAT_SENTENCE";
    public static final String TASK_TYPE_DESCRIBE_IMAGE = "DESCRIBE_IMAGE";
    public static final String TASK_TYPE_RE_TELL_LECTURE = "RE_TELL_LECTURE";
    public static final String TASK_TYPE_ANSWER_SHORT_QUESTION = "ANSWER_SHORT_QUESTION";
    public static final String TASK_TYPE_RESPOND_TO_A_SITUATION = "RESPOND_TO_A_SITUATION";
    public static final String TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION = "SUMMARIZE_GROUP_DISCUSSION";

    // RabbitMQ: a vendor call is slow/unreliable and needs bounded retry + a
    // DLQ, unlike objective scoring's synchronous path. Genuine async external
    // I/O — RabbitMQ stays in scope for this (global constraint: AI scoring,
    // email, media work), unlike the cross-module sync this migration removes.
    public static final String AI_SCORING_EXCHANGE = "scoring.ai-scoring";
    public static final String AI_SCORING_QUEUE = "scoring.ai-scoring-jobs";
    public static final String AI_SCORING_DLQ = "scoring.ai-scoring-jobs.dlq";
    public static final String AI_SCORING_ROUTING_KEY = "ai-scoring-job";

    private ScoringConstants() {
    }
}
