package com.pte.itembank.internal.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * The text formats the automatic scorers read from {@code correctAnswerText}. A value in any other shape is saved
 * without complaint but can never be scored, so it is rejected when the question is written. The rules are the ones
 * the scorers apply when they parse the key, so anything accepted here can be scored.
 */
public final class CorrectAnswerFormat {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CorrectAnswerFormat() {
    }

    /** True when the task type has no special format or {@code text} follows it. */
    public static boolean isValid(String taskTypeKey, String text) {
        if (taskTypeKey == null) {
            return true;
        }
        return switch (taskTypeKey) {
            case "FILL_IN_THE_BLANKS_TYPE_IN" -> isTypeInKey(text);
            case "HIGHLIGHT_INCORRECT_WORDS" -> isWordPositionList(text);
            case "ANSWER_SHORT_QUESTION" -> hasShortAnswer(text);
            default -> true;
        };
    }

    /** A JSON array of non-blank strings without commas (the submitted gaps travel comma-joined). */
    private static boolean isTypeInKey(String text) {
        JsonNode root = parseArray(text);
        if (root == null) {
            return false;
        }
        for (JsonNode element : root) {
            if (!element.isString() || element.asString().isBlank() || element.asString().contains(",")) {
                return false;
            }
        }
        return true;
    }

    /** A JSON array of distinct zero-based integer word positions. */
    private static boolean isWordPositionList(String text) {
        JsonNode root = parseArray(text);
        if (root == null) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonNode element : root) {
            if (!element.isIntegralNumber() || !element.canConvertToInt() || element.asInt() < 0
                    || !seen.add(element.asInt())) {
                return false;
            }
        }
        return true;
    }

    /** {@code canonical / alias / alias}: at least one answer with a word in it. */
    private static boolean hasShortAnswer(String text) {
        if (text == null) {
            return false;
        }
        for (String part : text.split("/")) {
            if (part.codePoints().anyMatch(Character::isLetterOrDigit)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode parseArray(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(text);
            return root != null && root.isArray() && !root.isEmpty() ? root : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
