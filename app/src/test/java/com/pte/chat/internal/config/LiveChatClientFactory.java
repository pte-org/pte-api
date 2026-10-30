package com.pte.chat.internal.config;

import com.pte.chat.internal.client.GeminiChatClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the production {@link GeminiChatClient} for the opt-in live prompt suite by calling the real
 * {@link ChatGeminiConfig} bean method after the production {@link ChatAiPropertiesValidator}, so the
 * pinned-model guard (gemini-2.5-flash-lite* only, no -latest alias) is the one production uses. Lives in this package because that method is package-private. The caller supplies the
 * key and model; every other setting keeps its {@link ChatAiProperties} default.
 */
public final class LiveChatClientFactory {

    private LiveChatClientFactory() {
    }

    public static ChatAiProperties properties(String apiKey, String model) {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey(apiKey);
        properties.setModel(model);
        return properties;
    }

    /** @throws IllegalStateException when the settings fail validation or the model is not in the pinned family */
    public static GeminiChatClient create(ChatAiProperties properties) {
        ChatAiPropertiesValidator.requireValid(properties);
        return new ChatGeminiConfig().geminiChatClient(properties, JsonMapper.builder().build());
    }
}
