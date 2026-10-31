package com.pte.chat.internal.constant;

import java.time.ZoneId;
import java.util.UUID;

/** Names and fixed values shared across the chat module. */
public final class ChatConstants {

    public static final String PROPERTY_PREFIX = "chat.ai";
    public static final String PROPERTY_ENABLED = PROPERTY_PREFIX + ".enabled";

    /** Quota days roll over at 00:00 in this zone; the server picks the day, never the client. */
    /** Replaces student and session ids when the retention job anonymises a row; no real id is all zeros. */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    /** What an email address or phone number in a question is replaced with before it is stored or sent. */
    public static final String MASK_EMAIL = "[email]";
    public static final String MASK_PHONE = "[phone]";

    public static final String ZONE_ID = "Asia/Ho_Chi_Minh";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    /** Length of {@code chat_message.question}; a larger {@code chat.ai.max-input-chars} would fail the insert. */
    public static final int MAX_QUESTION_CHARS = 4000;

    /** Length of the stored answer columns; the model's text is capped to this before any insert. */
    public static final int MAX_ANSWER_CHARS = 20_000;

    /** Languages a student can ask in; the answer follows the question, this only picks the fixed refusal text. */
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_VI = "vi";

    /** Persisted {@code chat_message.status} values; the V7 CHECK constraint allows exactly these. */
    public static final String STATUS_OK = "OK";
    public static final String STATUS_REFUSED = "REFUSED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String CHAT_PATH = "/api/v1/student/chat";
    public static final String MESSAGES_PATH = "/messages";
    public static final String QUOTA_PATH = "/quota";
    public static final String ADMIN_USAGE_PATH = "/api/v1/admin/chat/usage";
    public static final String ADMIN_STUDENTS_PATH = "/students";



    /** Stable machine codes the web client maps to its own copy; the English user messages are only a fallback. */
    public static final String ERR_QUOTA_EXCEEDED = "CHAT_QUOTA_EXCEEDED";
    public static final String ERR_REQUEST_IN_FLIGHT = "CHAT_REQUEST_IN_FLIGHT";
    public static final String ERR_BUSY = "CHAT_BUSY";
    public static final String ERR_INVALID_REQUEST = "CHAT_INVALID_REQUEST";

    /** Defaults for {@code chat.ai.retention-*}; the privacy hand-off fixes 12 months of text and 30 days of cache. */
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    /** Student-facing error messages; the code above is what the web client keys on. */
    public static final String BUSY_MESSAGE =
            "The assistant is busy right now. Your message was not counted. Please try again in a moment.";
    public static final String QUOTA_EXCEEDED_MESSAGE =
            "You have used all of today's chat messages. Please come back after the reset time.";
    public static final String REQUEST_IN_FLIGHT_MESSAGE =
            "Your previous question is still being answered. Please wait for it to finish.";
    /** Takes the field detail below, never the value: the value may be a student's private text. */
    public static final String INVALID_REQUEST_MESSAGE_TEMPLATE = "Your message could not be sent: %s.";

    public static final String DETAIL_MESSAGE_EMPTY = "message is empty";
    public static final String DETAIL_MESSAGE_TOO_LONG = "message is too long";
    public static final String DETAIL_MESSAGE_UNSUPPORTED_CHARS = "message has unsupported characters";
    public static final String DETAIL_TASK_TYPE_UNSUPPORTED = "task type is not supported";
    /** Longest exercise text sent as context; the web client cuts at the same length (shared contract file). */
    public static final int EXERCISE_PROMPT_MAX_CHARS = 1500;
    public static final String DETAIL_HISTORY_INVALID = "history is invalid";
    public static final String HISTORY_ROLE_USER = "user";
    public static final String HISTORY_ROLE_ASSISTANT = "assistant";
    /** One earlier turn: as long as an answer may be, so a real answer can always be sent back as context. */
    public static final int HISTORY_ITEM_MAX_CHARS = MAX_ANSWER_CHARS;
    /** All earlier turns together, so a long conversation cannot grow the provider bill without limit. */
    public static final int HISTORY_TOTAL_MAX_CHARS = 24000;
    public static final String DETAIL_LANGUAGE_UNSUPPORTED = "language is not supported";
    public static final String DETAIL_REPORT_RANGE_INVALID = "the date range must start before it ends and cover at most a year";

    /** Bean validation messages for the request body. */
    public static final String MESSAGE_REQUIRED_MESSAGE = "message is required";
    public static final String HISTORY_TOO_LONG_MESSAGE = "history has too many messages";
    /** Most entries the request body may carry; the validator then keeps only the newest few. */
    public static final int HISTORY_MAX_ITEMS = 100;

    /** Keys of the {@code data} object sent with a quota-exceeded error. */
    public static final String DATA_RESET_AT = "resetAt";
    public static final String DATA_LIMIT = "limit";
    public static final String DATA_REMAINING = "remaining";

    /** Internal failure messages: they name the problem, never the student's text or the provider's body. */
    public static final String PARSE_NO_TEXT_MESSAGE = "Chat model returned no text";
    public static final String PARSE_ON_TOPIC_INVALID_MESSAGE = "Chat model field is_on_topic is missing or not a boolean";
    public static final String PARSE_MARKDOWN_MISSING_MESSAGE = "Chat model field markdown_content is missing or blank";
    public static final String PARSE_MARKDOWN_TOO_LONG_MESSAGE = "Chat model field markdown_content is too long";
    public static final String PARSE_UNTERMINATED_FENCE_MESSAGE = "Chat model output has an unterminated code fence";
    public static final String PARSE_NOT_JSON_MESSAGE = "Chat model output is not valid JSON";
    public static final String PARSE_NOT_OBJECT_MESSAGE = "Chat model output is not a JSON object";

    public static final String PROVIDER_HTTP_STATUS_MESSAGE = "Chat provider answered HTTP ";
    public static final String PROVIDER_TIMEOUT_MESSAGE = "Chat provider timed out";
    public static final String PROVIDER_UNREACHABLE_MESSAGE = "Chat provider could not be reached";
    public static final String PROVIDER_INTERRUPTED_MESSAGE = "Chat provider call was interrupted";
    public static final String PROVIDER_BODY_NOT_JSON_MESSAGE = "Chat provider body is not JSON";
    public static final String PROVIDER_PROMPT_BLOCKED_MESSAGE = "Chat prompt was blocked";
    public static final String PROVIDER_NO_CANDIDATES_MESSAGE = "Chat provider returned no candidates";
    public static final String PROVIDER_NO_FINISH_REASON_MESSAGE = "Chat answer has no finish reason";
    public static final String PROVIDER_ANSWER_BLOCKED_MESSAGE = "Chat answer was blocked";
    public static final String PROVIDER_TOKEN_LIMIT_MESSAGE = "Chat answer hit the token limit";
    public static final String PROVIDER_UNEXPECTED_END_MESSAGE = "Chat answer ended unexpectedly";

    /** Startup configuration problems, reported together by the validator. */
    public static final String CONFIG_INVALID_PREFIX = "Invalid chat.ai settings: ";
    public static final String CONFIG_API_KEY_MESSAGE =
            "chat.ai.api-key must be set and contain no whitespace (CHAT_AI_GEMINI_API_KEY)";
    public static final String CONFIG_MODEL_UNSET_MESSAGE =
            "chat.ai.model must be set to a pinned model name (CHAT_AI_MODEL)";
    public static final String CONFIG_MODEL_CHARS_MESSAGE =
            "chat.ai.model may only contain letters, digits, '.', '_' and '-'";
    public static final String CONFIG_MODEL_LATEST_MESSAGE = "chat.ai.model must be a pinned model, not a '-latest' alias";
    public static final String CONFIG_BASE_URL_MESSAGE =
            "chat.ai.base-url must be an http or https address with a host (CHAT_AI_BASE_URL)";
    public static final String CONFIG_NOT_NEGATIVE_TEMPLATE ="%s must be set and not negative";
    public static final String CONFIG_AT_LEAST_ONE_TEMPLATE ="%s must be at least 1 (was %d)";
    public static final String CONFIG_TEMPERATURE_TEMPLATE = "chat.ai.temperature must be between %s and %s";
    public static final String CONFIG_MAX_INPUT_TEMPLATE =
            "chat.ai.max-input-chars must be at most %d (the question column length)";
    public static final String CONFIG_MODEL_UNSUPPORTED_TEMPLATE =
            "chat.ai.model '%s' is not supported: only these families are verified (thinking off or minimal): %s";

    private ChatConstants() {
    }
}
