package com.pte.chat.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code chat.ai.cache-max-question-chars}: the longest question that may enter the shared cache. */
class ChatCacheQuestionLimitPropertiesTest {

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("AIza-test-key_123");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    @Test
    void theDefaultIsThreeHundred() {
        assertThat(new ChatAiProperties().getCacheMaxQuestionChars()).isEqualTo(300);
    }

    @Test
    void bindsTheKebabCaseName() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("chat.ai.cache-max-question-chars", "120")));

        assertThat(binder.bind("chat.ai", ChatAiProperties.class).get().getCacheMaxQuestionChars()).isEqualTo(120);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void aNonPositiveValueIsReportedByName(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setCacheMaxQuestionChars(value);

        assertThat(ChatAiPropertiesValidator.validate(properties))
                .anyMatch(problem -> problem.startsWith("chat.ai.cache-max-question-chars"));
    }
}
