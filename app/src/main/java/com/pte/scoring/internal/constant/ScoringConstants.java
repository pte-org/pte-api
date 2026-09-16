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

    public static final String ANSWER_NOT_FOUND = "ANSWER_NOT_FOUND";
    public static final String INVALID_ANSWER_STATUS = "INVALID_ANSWER_STATUS";
    public static final String UNSUPPORTED_TASK_TYPE = "UNSUPPORTED_TASK_TYPE";
    public static final String AI_PROVIDER_INVALID = "scoring.ai.provider must be stub or openai-compatible";
    public static final String AI_SCORING_RETRIES_EXHAUSTED = "AI scoring retries exhausted";
    public static final String UNSUPPORTED_AI_TASK_TYPE = "Unsupported AI task type: %s";
    public static final String RAW_SCORE_RANGE_INVALID = "rawScore must be between 0 and 100";
    public static final String SUBSCORE_NAME_BLANK = "subScore names must not be blank";
    public static final String SUBSCORES_RANGE_INVALID = "subScores must be between 0 and 100";
    public static final String AI_MODEL_NOT_CONFIGURED = "AI model is not configured";
    public static final String AI_REQUEST_NO_MESSAGES = "AI request has no messages";
    public static final String AI_REQUEST_SERIALIZATION_FAILED = "Could not serialize AI request";
    public static final String AI_PROVIDER_REQUEST_FAILED = "AI provider request failed";
    public static final String AI_EMPTY_RESPONSE = "AI provider returned an empty response";
    public static final String AI_RESPONSE_CONTENT_NOT_TEXT = "choices[0].message.content is not text";
    public static final String RAW_SCORE_NOT_INTEGER = "rawScore must be an integer";
    public static final String AI_RESPONSE_PARSE_FAILED = "Could not parse AI provider response";
    public static final String SUBSCORES_NOT_OBJECT = "subScores must be an object";
    public static final String SUBSCORES_MUST_CONTAIN_INTEGERS = "subScores must contain integers";
    public static final String MALFORMED_JSON_MARKDOWN_FENCE = "Malformed JSON markdown fence";
    public static final String ESSAY_RESPONSE_EMPTY = "Essay response is empty";
    public static final String ESSAY_PROMPT_EMPTY = "Essay prompt is empty";
    public static final String OPENAI_SETTING_REQUIRED = "%s is required when scoring.ai.provider=openai-compatible";
    public static final String SPEECH_MEDIA_ID_INVALID = "Speech answer does not contain a valid media ID";
    public static final String SPEECH_TENANT_ID_MISSING = "Speech answer is missing tenant ID";
    public static final String SPEECH_REFERENCE_TEXT_EMPTY = "Speech reference text is empty";
    public static final String SPEECH_MEDIA_RESOLUTION_FAILED = "Speech media resolution failed";
    public static final String SPEECH_MEDIA_UNRESOLVED = "Speech media could not be resolved";
    public static final String SPEECH_MEDIA_DOWNLOAD_FAILED = "Speech media download failed";
    public static final String SPEECH_MEDIA_EMPTY = "Speech media download was empty";

    private ScoringConstants() {
    }
}
