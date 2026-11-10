package com.pte.chat.internal.config;

import com.pte.chat.internal.client.OpenAiChatClient;
import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Uses the question-generation endpoint without coupling chat to itembank internals. */
@Configuration
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatOpenAiConfig {

    @Bean
    @ConditionalOnProperty(name = ChatConstants.PROPERTY_PROVIDER, havingValue = ChatConstants.PROVIDER_OPENAI_COMPATIBLE)
    OpenAiChatClient openAiChatClient(ChatAiProperties properties, JsonMapper jsonMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        return new OpenAiChatClient(httpClient, jsonMapper, URI.create(properties.getBaseUrl().strip()),
                properties.getApiKey(), properties.getModel(), properties.getTemperature(),
                properties.getMaxTokens(), Duration.ofMillis(properties.getReadTimeoutMs()));
    }
}
