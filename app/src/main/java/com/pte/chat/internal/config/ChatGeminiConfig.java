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
 * <p>Thinking tokens count against {@code maxOutputTokens}, and the request does not set a thinking level.
 * ai.google.dev/gemini-api/docs/thinking (checked 2026-10-28) lists thinking as off by default for
 * gemini-2.5-flash-lite and as on (minimal) for gemini-3.5-flash-lite, but gives no verified REST field
 * for the level. Until one is verified and sent, only the 2.5 Flash-Lite family is accepted, so a model
 * swap cannot silently spend the answer budget on reasoning.
 */
@Configuration
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatGeminiConfig {

    private static final URI GEMINI_BASE_URI = URI.create("https://generativelanguage.googleapis.com");
    private static final String THINKING_OFF_MODEL_PREFIX = "gemini-2.5-flash-lite";

    @Bean
    GeminiChatClient geminiChatClient(ChatAiProperties properties, JsonMapper jsonMapper) {
        String model = properties.getModel();
        if (model == null || !model.startsWith(THINKING_OFF_MODEL_PREFIX)) {
            throw new IllegalStateException(
                    ChatConstants.CONFIG_MODEL_UNSUPPORTED_TEMPLATE.formatted(model, THINKING_OFF_MODEL_PREFIX));
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        return new GeminiChatClient(httpClient, jsonMapper, GEMINI_BASE_URI, properties.getApiKey(),
                properties.getModel(), properties.getTemperature(), properties.getMaxTokens(),
                Duration.ofMillis(properties.getReadTimeoutMs()));
    }
}
