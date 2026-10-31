package com.pte.chat.internal.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAiPropertiesValidatorTest {

    private static final String VALID_KEY = "AIza-test-key_123";
    private static final String VALID_MODEL = "gemini-2.5-flash-lite";

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey(VALID_KEY);
        properties.setModel(VALID_MODEL);
        return properties;
    }

    @Test
    void defaults_match_the_design_contract() {
        ChatAiProperties properties = new ChatAiProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isEmpty();
        assertThat(properties.getModel()).isEmpty();
        assertThat(properties.getTemperature()).isEqualTo(0.2);
        assertThat(properties.getMaxTokens()).isEqualTo(800);
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(2000);
        assertThat(properties.getReadTimeoutMs()).isEqualTo(30000);
        assertThat(properties.getDailyLimit()).isEqualTo(20);
        assertThat(properties.getMaxInputChars()).isEqualTo(1000);
        assertThat(properties.getHistorySize()).isEqualTo(6);
        assertThat(properties.getMaxConcurrent()).isEqualTo(20);
    }

    @Test
    void disabled_returns_no_problems_even_with_garbage() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(false);
        properties.setApiKey("  bad key \n");
        properties.setModel("gemini-flash-latest/ x");
        properties.setDailyLimit(0);
        properties.setMaxInputChars(0);
        properties.setMaxTokens(0);
        properties.setHistorySize(0);
        properties.setMaxConcurrent(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @Test
    void enabled_with_defaults_and_valid_key_and_model_returns_no_problems() {
        assertThat(ChatAiPropertiesValidator.validate(validEnabled())).isEmpty();
    }

    @Test
    void enabled_with_blank_api_key_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setApiKey("   ");

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_empty_api_key_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setApiKey("");

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_whitespace_or_newline_in_api_key_is_reported() {
        ChatAiProperties withSpace = validEnabled();
        withSpace.setApiKey("AIza abc");
        ChatAiProperties withNewline = validEnabled();
        withNewline.setApiKey("AIzaabc\n");

        assertThat(ChatAiPropertiesValidator.validate(withSpace)).hasSize(1);
        assertThat(ChatAiPropertiesValidator.validate(withNewline)).hasSize(1);
    }

    @Test
    void enabled_with_blank_model_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setModel(" ");

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_latest_alias_model_is_rejected() {
        ChatAiProperties properties = validEnabled();
        properties.setModel("gemini-flash-lite-latest");

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).containsIgnoringCase("latest");
    }

    @Test
    void latest_alias_check_is_case_insensitive() {
        ChatAiProperties properties = validEnabled();
        properties.setModel("Gemini-2.5-flash-lite-LATEST");

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_space_in_model_is_rejected() {
        ChatAiProperties properties = validEnabled();
        properties.setModel("gemini 2.5");

        assertThat(ChatAiPropertiesValidator.validate(properties)).isNotEmpty();
    }

    @Test
    void enabled_with_slash_in_model_is_rejected() {
        ChatAiProperties properties = validEnabled();
        properties.setModel("models/gemini-2.5-flash-lite");

        assertThat(ChatAiPropertiesValidator.validate(properties)).isNotEmpty();
    }

    @Test
    void enabled_with_zero_daily_limit_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setDailyLimit(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_zero_max_input_chars_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setMaxInputChars(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_zero_max_tokens_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setMaxTokens(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_zero_history_size_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setHistorySize(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void enabled_with_zero_max_concurrent_is_reported() {
        ChatAiProperties properties = validEnabled();
        properties.setMaxConcurrent(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    @Test
    void require_valid_is_silent_for_valid_enabled_properties() {
        assertThatCode(() -> ChatAiPropertiesValidator.requireValid(validEnabled())).doesNotThrowAnyException();
    }

    @Test
    void require_valid_is_silent_when_disabled_even_with_garbage() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(false);
        properties.setApiKey("bad key");
        properties.setModel("x-latest");
        properties.setDailyLimit(-5);

        assertThatCode(() -> ChatAiPropertiesValidator.requireValid(properties)).doesNotThrowAnyException();
    }

    @Test
    void require_valid_aggregates_every_problem_into_one_exception() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("");
        properties.setModel("gemini-flash-latest");
        properties.setDailyLimit(0);
        properties.setMaxInputChars(0);
        properties.setMaxTokens(0);
        properties.setHistorySize(0);
        properties.setMaxConcurrent(0);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(7).doesNotContainNull();
        assertThatThrownBy(() -> ChatAiPropertiesValidator.requireValid(properties))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> problems.forEach(problem -> assertThat(error.getMessage()).contains(problem)));
    }
}
