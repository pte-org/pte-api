package com.pte.chat.internal.constant;

import java.time.ZoneId;

/** Names and fixed values shared across the chat module. */
public final class ChatConstants {

    public static final String PROPERTY_PREFIX = "chat.ai";
    public static final String PROPERTY_ENABLED = PROPERTY_PREFIX + ".enabled";

    /** Quota days roll over at 00:00 in this zone; the server picks the day, never the client. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
    public static final String LATEST_SESSION_PATH = "/sessions/latest";

    /** Most messages the "latest session" endpoint returns, so a very long conversation cannot bloat a reload. */
    public static final int DISPLAY_MESSAGE_LIMIT = 50;

    /** Stable machine codes the web client maps to its own copy; the English user messages are only a fallback. */
    public static final String ERR_QUOTA_EXCEEDED = "CHAT_QUOTA_EXCEEDED";
    public static final String ERR_REQUEST_IN_FLIGHT = "CHAT_REQUEST_IN_FLIGHT";
    public static final String ERR_BUSY = "CHAT_BUSY";
    public static final String ERR_SESSION_NOT_FOUND = "CHAT_SESSION_NOT_FOUND";
    public static final String ERR_INVALID_REQUEST = "CHAT_INVALID_REQUEST";

    private ChatConstants() {
    }
}
