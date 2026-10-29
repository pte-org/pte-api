package com.pte.chat.internal.config;

import com.pte.chat.internal.constant.ChatConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Startup checks for {@code chat.ai.*}. Every problem is reported at once so a deploy is not fixed one
 * restart per variable. Disabled chat is never validated: the app must boot without a key.
 */
public final class ChatAiPropertiesValidator {

    // Keys travel in a header; a pasted space or newline would only surface as a 401 per answer.
    private static final Pattern API_KEY = Pattern.compile("\\p{Graph}+");
    // The model name is spliced into the vendor URL, so only plain names pass.
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._-]+");
    // An alias silently moves to another model, changing price and behaviour without a deploy.
    private static final String LATEST_ALIAS = "-latest";
    // Gemini rejects values outside this range with a 400 on every request.
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 2.0;

    private ChatAiPropertiesValidator() {
    }

    public static List<String> validate(ChatAiProperties properties) {
        List<String> problems = new ArrayList<>();
        if (!properties.isEnabled()) {
            return problems;
        }
        String apiKey = properties.getApiKey();
        if (apiKey == null || !API_KEY.matcher(apiKey).matches()) {
            problems.add("chat.ai.api-key must be set and contain no whitespace (CHAT_AI_GEMINI_API_KEY)");
        }
        String model = properties.getModel();
        if (model == null || model.isBlank()) {
            problems.add("chat.ai.model must be set to a pinned model name (CHAT_AI_MODEL)");
        } else {
            if (!MODEL.matcher(model).matches()) {
                problems.add("chat.ai.model may only contain letters, digits, '.', '_' and '-'");
            }
            if (model.toLowerCase().contains(LATEST_ALIAS)) {
                problems.add("chat.ai.model must be a pinned model, not a '-latest' alias");
            }
        }
        requireAtLeastOne(problems, "chat.ai.daily-limit", properties.getDailyLimit());
        requireAtLeastOne(problems, "chat.ai.max-input-chars", properties.getMaxInputChars());
        requireAtLeastOne(problems, "chat.ai.max-tokens", properties.getMaxTokens());
        requireAtLeastOne(problems, "chat.ai.history-size", properties.getHistorySize());
        requireAtLeastOne(problems, "chat.ai.max-concurrent", properties.getMaxConcurrent());
        requireAtLeastOne(problems, "chat.ai.max-cache-hits-per-day", properties.getMaxCacheHitsPerDay());
        // A zero timeout can mean "wait forever" to an HTTP client, which would pin a request thread.
        requireAtLeastOne(problems, "chat.ai.connect-timeout-ms", properties.getConnectTimeoutMs());
        requireAtLeastOne(problems, "chat.ai.read-timeout-ms", properties.getReadTimeoutMs());
        double temperature = properties.getTemperature();
        if (Double.isNaN(temperature) || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            problems.add("chat.ai.temperature must be between " + MIN_TEMPERATURE + " and " + MAX_TEMPERATURE);
        }
        if (properties.getMaxInputChars() > ChatConstants.MAX_QUESTION_CHARS) {
            problems.add("chat.ai.max-input-chars must be at most " + ChatConstants.MAX_QUESTION_CHARS
                    + " (the question column length)");
        }
        return problems;
    }

    public static void requireValid(ChatAiProperties properties) {
        List<String> problems = validate(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid chat.ai settings: " + String.join("; ", problems));
        }
    }

    private static void requireAtLeastOne(List<String> problems, String name, int value) {
        if (value < 1) {
            problems.add(name + " must be at least 1 (was " + value + ")");
        }
    }
}
