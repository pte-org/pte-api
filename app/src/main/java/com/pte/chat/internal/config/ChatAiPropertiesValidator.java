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
            problems.add(ChatConstants.CONFIG_API_KEY_MESSAGE);
        }
        String model = properties.getModel();
        if (model == null || model.isBlank()) {
            problems.add(ChatConstants.CONFIG_MODEL_UNSET_MESSAGE);
        } else {
            if (!MODEL.matcher(model).matches()) {
                problems.add(ChatConstants.CONFIG_MODEL_CHARS_MESSAGE);
            }
            if (model.toLowerCase().contains(LATEST_ALIAS)) {
                problems.add(ChatConstants.CONFIG_MODEL_LATEST_MESSAGE);
            }
        }
        requireAtLeastOne(problems, "chat.ai.daily-limit", properties.getDailyLimit());
        requireAtLeastOne(problems, "chat.ai.max-input-chars", properties.getMaxInputChars());
        requireAtLeastOne(problems, "chat.ai.max-tokens", properties.getMaxTokens());
        requireAtLeastOne(problems, "chat.ai.history-size", properties.getHistorySize());
        requireAtLeastOne(problems, "chat.ai.max-concurrent", properties.getMaxConcurrent());
        requireAtLeastOne(problems, "chat.ai.max-cache-hits-per-day", properties.getMaxCacheHitsPerDay());
        requireAtLeastOne(problems, "chat.ai.retention-content-months", properties.getRetentionContentMonths());
        requireAtLeastOne(problems, "chat.ai.retention-cache-days", properties.getRetentionCacheDays());
        requireAtLeastOne(problems, "chat.ai.retention-batch-size", properties.getRetentionBatchSize());
        // A zero timeout can mean "wait forever" to an HTTP client, which would pin a request thread.
        requireAtLeastOne(problems, "chat.ai.connect-timeout-ms", properties.getConnectTimeoutMs());
        requireAtLeastOne(problems, "chat.ai.read-timeout-ms", properties.getReadTimeoutMs());
        double temperature = properties.getTemperature();
        if (Double.isNaN(temperature) || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            problems.add(ChatConstants.CONFIG_TEMPERATURE_TEMPLATE.formatted(MIN_TEMPERATURE, MAX_TEMPERATURE));
        }
        if (properties.getMaxInputChars() > ChatConstants.MAX_QUESTION_CHARS) {
            problems.add(ChatConstants.CONFIG_MAX_INPUT_TEMPLATE.formatted(ChatConstants.MAX_QUESTION_CHARS));
        }
        return problems;
    }

    public static void requireValid(ChatAiProperties properties) {
        List<String> problems = validate(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(ChatConstants.CONFIG_INVALID_PREFIX + String.join("; ", problems));
        }
    }

    private static void requireAtLeastOne(List<String> problems, String name, int value) {
        if (value < 1) {
            problems.add(ChatConstants.CONFIG_AT_LEAST_ONE_TEMPLATE.formatted(name, value));
        }
    }
}
