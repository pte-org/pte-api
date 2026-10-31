package com.pte.chat.internal.client;

import com.pte.chat.internal.constant.ChatConstants;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the model's text into a {@link ChatModelOutput}. The text is untrusted: only a single JSON object
 * with a boolean {@code is_on_topic} (and, when on topic, a non-blank {@code markdown_content}) passes.
 * Nothing is repaired or guessed, and failures never repeat the text, which may hold the student's words.
 */
public final class ChatOutputParser {

    public static final int MAX_MARKDOWN_CHARS = ChatConstants.MAX_ANSWER_CHARS;

    private static final String FENCE = "```";
    private static final String FENCE_LANGUAGE = "json";
    // Text after the object is a sign the model rambled; reject it instead of reading the first object.
    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private ChatOutputParser() {
    }

    public static ChatModelOutput parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw invalid(ChatConstants.PARSE_NO_TEXT_MESSAGE);
        }
        JsonNode root = readObject(stripFence(rawText.strip()));
        JsonNode onTopic = root.get("is_on_topic");
        if (onTopic == null || !onTopic.isBoolean()) {
            throw invalid(ChatConstants.PARSE_ON_TOPIC_INVALID_MESSAGE);
        }
        if (!onTopic.asBoolean()) {
            return new ChatModelOutput(false, "");
        }
        JsonNode markdown = root.get("markdown_content");
        if (markdown == null || !markdown.isString() || markdown.asString().isBlank()) {
            throw invalid(ChatConstants.PARSE_MARKDOWN_MISSING_MESSAGE);
        }
        if (markdown.asString().length() > MAX_MARKDOWN_CHARS) {
            throw invalid(ChatConstants.PARSE_MARKDOWN_TOO_LONG_MESSAGE);
        }
        return new ChatModelOutput(true, markdown.asString());
    }

    /** Some models still wrap JSON in a markdown fence despite the response mime type. */
    private static String stripFence(String text) {
        if (!text.startsWith(FENCE)) {
            return text;
        }
        String inner = text.substring(FENCE.length());
        if (inner.startsWith(FENCE_LANGUAGE)) {
            inner = inner.substring(FENCE_LANGUAGE.length());
        }
        inner = inner.strip();
        if (!inner.endsWith(FENCE)) {
            throw invalid(ChatConstants.PARSE_UNTERMINATED_FENCE_MESSAGE);
        }
        return inner.substring(0, inner.length() - FENCE.length()).strip();
    }

    private static JsonNode readObject(String text) {
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (JacksonException ex) {
            // The parser message quotes the input, so the cause is dropped on purpose.
            throw invalid(ChatConstants.PARSE_NOT_JSON_MESSAGE);
        }
        if (root == null || !root.isObject()) {
            throw invalid(ChatConstants.PARSE_NOT_OBJECT_MESSAGE);
        }
        return root;
    }

    private static ChatAiException invalid(String message) {
        return new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE, message);
    }
}
