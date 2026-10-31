package com.pte.chat.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code chat.ai.max-cache-hits-per-day}: default, validation and binding. */
class ChatPropertiesCacheCapTest {

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("AIza-test-key_123");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    @Test
    void theDefaultIsOneHundred() {
        assertThat(new ChatAiProperties().getMaxCacheHitsPerDay()).isEqualTo(100);
    }

    @Test
    void enabledWithTheDefaultIsValid() {
        assertThat(ChatAiPropertiesValidator.validate(validEnabled())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100, 100_000})
    void enabledWithAPositiveValueIsValid(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setMaxCacheHitsPerDay(value);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    void enabledWithZeroOrANegativeValueReportsExactlyOneProblemNamingTheKey(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setMaxCacheHitsPerDay(value);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).contains("chat.ai.max-cache-hits-per-day").contains("at least 1")
                .contains("was " + value);
    }

    @Test
    void requireValidFailsStartupForABadValue() {
        ChatAiProperties properties = validEnabled();
        properties.setMaxCacheHitsPerDay(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ChatAiPropertiesValidator.requireValid(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat.ai.max-cache-hits-per-day");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void disabledNeverReportsIt(int value) {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(false);
        properties.setMaxCacheHitsPerDay(value);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @Test
    void theValueBindsFromTheKebabCaseKey() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("chat.ai.max-cache-hits-per-day", "7")));

        ChatAiProperties bound = binder.bind("chat.ai", ChatAiProperties.class).get();

        assertThat(bound.getMaxCacheHitsPerDay()).isEqualTo(7);
    }

    @Test
    void theValueBindsFromAnEnvironmentStyleKeyAndTheDefaultSurvivesWhenAbsent() {
        Binder envBinder = new Binder(new MapConfigurationPropertySource(Map.of("chat.ai.maxCacheHitsPerDay", "12")));
        Binder emptyBinder = new Binder(new MapConfigurationPropertySource(Map.of("chat.ai.daily-limit", "3")));

        assertThat(envBinder.bind("chat.ai", ChatAiProperties.class).get().getMaxCacheHitsPerDay()).isEqualTo(12);
        ChatAiProperties withoutKey = emptyBinder.bind("chat.ai", ChatAiProperties.class).get();
        assertThat(withoutKey.getMaxCacheHitsPerDay()).isEqualTo(100);
        assertThat(withoutKey.getDailyLimit()).isEqualTo(3);
    }
}
