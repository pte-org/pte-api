package com.pte.chat.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code chat.ai.base-url}: the provider's address. It defaults to Google's and can point at a proxy or a
 * stand-in server for staging and end-to-end checks; it is deployment-owned, never student input.
 */
class ChatBaseUrlPropertiesTest {

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("AIza-test-key_123");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    @Test
    void itDefaultsToGoogle() {
        assertThat(new ChatAiProperties().getBaseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
        assertThat(ChatAiPropertiesValidator.validate(validEnabled())).isEmpty();
    }

    @Test
    void bindsTheKebabCaseName() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("chat.ai.base-url", "http://localhost:59999")));

        assertThat(binder.bind("chat.ai", ChatAiProperties.class).get().getBaseUrl()).isEqualTo("http://localhost:59999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:59999", "https://proxy.example.com", "https://proxy.example.com:8443"})
    void anHttpOrHttpsAddressWithAHostIsAccepted(String url) {
        ChatAiProperties properties = validEnabled();
        properties.setBaseUrl(url);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "ftp://example.com", "file:///etc/passwd", "localhost:59999", "https://", "//example.com"})
    void anythingElseIsReportedByName(String url) {
        ChatAiProperties properties = validEnabled();
        properties.setBaseUrl(url);

        assertThat(ChatAiPropertiesValidator.validate(properties)).anyMatch(problem -> problem.startsWith("chat.ai.base-url"));
    }

    @Test
    void aNullAddressIsReportedToo() {
        ChatAiProperties properties = validEnabled();
        properties.setBaseUrl(null);

        assertThat(ChatAiPropertiesValidator.validate(properties)).anyMatch(problem -> problem.startsWith("chat.ai.base-url"));
    }

    @Test
    void theClientIsBuiltAgainstTheConfiguredAddress() {
        ChatAiProperties properties = validEnabled();
        properties.setBaseUrl("http://localhost:59999");

        // Building the client must not touch the network; it only needs to accept the address.
        assertThat(new ChatGeminiConfig().geminiChatClient(properties, JsonMapper.builder().build())).isNotNull();
    }

    @Test
    void disabledChatIsNeverValidated() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setBaseUrl("nonsense");

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }
}
