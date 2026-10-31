package com.pte.chat.internal.config;

import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The chat's own Gemini client, built from {@code chat.ai.*}; it exists only while chat is enabled. It needs
 * {@link ChatAiProperties}, which {@link ChatAiConfig} registers and validates, so load both together.
 *
 * <p>Thinking tokens count against {@code maxOutputTokens}. ai.google.dev/gemini-api/docs/thinking lists thinking
 * as off by default for gemini-2.5-flash-lite and as on (minimal) for gemini-3.5-flash-lite. The 3.5 model is sent
 * {@code thinkingConfig.thinkingLevel = minimal} (accepted by the live API on 2026-10-29). Only these two families
 * are accepted, so a model swap cannot silently spend the answer budget on reasoning.
 */
@Configuration
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatGeminiConfig {

    /** Thinking is off by default: nothing to send. */
    private static final String THINKING_OFF_MODEL_PREFIX = "gemini-2.5-flash-lite";
    /** Thinks by default (minimal): the lowest level is requested so reasoning does not eat the answer budget. */
    private static final String THINKING_MINIMAL_MODEL_PREFIX = "gemini-3.5-flash-lite";
    private static final String MINIMAL_LEVEL = "minimal";

    /** The thinking level to send for a supported model, or null to leave the model's default (thinking off). */
    static String thinkingLevelFor(String model) {
        return model != null && model.startsWith(THINKING_MINIMAL_MODEL_PREFIX) ? MINIMAL_LEVEL : null;
    }

    @Bean
    GeminiChatClient geminiChatClient(ChatAiProperties properties, JsonMapper jsonMapper) {
        String model = properties.getModel();
        if (model == null
                || !(model.startsWith(THINKING_OFF_MODEL_PREFIX) || model.startsWith(THINKING_MINIMAL_MODEL_PREFIX))) {
            throw new IllegalStateException(ChatConstants.CONFIG_MODEL_UNSUPPORTED_TEMPLATE.formatted(
                    model, THINKING_OFF_MODEL_PREFIX + ", " + THINKING_MINIMAL_MODEL_PREFIX));
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        return new GeminiChatClient(httpClient, jsonMapper, URI.create(properties.getBaseUrl().strip()), properties.getApiKey(),
                model, properties.getTemperature(), properties.getMaxTokens(),
                Duration.ofMillis(properties.getReadTimeoutMs()), thinkingLevelFor(model));
    }
}
