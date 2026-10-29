package com.pte.chat.internal.config;

import com.pte.chat.internal.constant.ChatConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 additions to the {@code chat.ai.*} startup checks: timeouts, temperature and the input cap. Each
 * bad field is exactly one problem message, so a deploy can read which variable to fix.
 */
class ChatAiPropertiesRangeTest {

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("AIza-test-key_123");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    @Test
    void maxQuestionCharsMatchesTheColumnLength() {
        assertThat(ChatConstants.MAX_QUESTION_CHARS).isEqualTo(4000);
    }

    @Test
    void defaultsStayValid() {
        assertThat(ChatAiPropertiesValidator.validate(validEnabled())).isEmpty();
    }

    // ---- timeouts ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void connectTimeoutBelowOneIsOneProblem(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setConnectTimeoutMs(value);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("connect-timeout-ms");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void readTimeoutBelowOneIsOneProblem(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setReadTimeoutMs(value);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("read-timeout-ms");
    }

    @Test
    void timeoutsOfOneMillisecondAreValid() {
        ChatAiProperties properties = validEnabled();
        properties.setConnectTimeoutMs(1);
        properties.setReadTimeoutMs(1);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    // ---- temperature -------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.2, 1.0, 1.999, 2.0})
    void temperatureInsideZeroToTwoIsValid(double value) {
        ChatAiProperties properties = validEnabled();
        properties.setTemperature(value);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {2.1, -0.1, 3.5, -1.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void temperatureOutsideZeroToTwoIsOneProblem(double value) {
        ChatAiProperties properties = validEnabled();
        properties.setTemperature(value);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("temperature");
    }

    @Test
    void nanTemperatureIsOneProblem() {
        ChatAiProperties properties = validEnabled();
        properties.setTemperature(Double.NaN);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("temperature");
    }

    // ---- input cap ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 1000, 3999, 4000})
    void maxInputCharsUpToTheColumnLengthIsValid(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setMaxInputChars(value);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {4001, 5000, Integer.MAX_VALUE})
    void maxInputCharsAboveTheColumnLengthIsOneProblem(int value) {
        ChatAiProperties properties = validEnabled();
        properties.setMaxInputChars(value);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("max-input-chars");
    }

    @Test
    void maxInputCharsOfZeroStaysASingleProblem() {
        ChatAiProperties properties = validEnabled();
        properties.setMaxInputChars(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).hasSize(1);
    }

    // ---- aggregation and the disabled switch -------------------------------------------------------------

    @Test
    void everyNewProblemIsReportedTogether() {
        ChatAiProperties properties = validEnabled();
        properties.setConnectTimeoutMs(0);
        properties.setReadTimeoutMs(0);
        properties.setTemperature(2.5);
        properties.setMaxInputChars(ChatConstants.MAX_QUESTION_CHARS + 1);

        List<String> problems = ChatAiPropertiesValidator.validate(properties);

        assertThat(problems).hasSize(4).doesNotContainNull();
        assertThatThrownBy(() -> ChatAiPropertiesValidator.requireValid(properties))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> problems.forEach(problem -> assertThat(error.getMessage()).contains(problem)));
    }

    @Test
    void disabledPropertiesAreNeverValidatedEvenWhenOutOfRange() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(false);
        properties.setConnectTimeoutMs(0);
        properties.setReadTimeoutMs(-5);
        properties.setTemperature(Double.NaN);
        properties.setMaxInputChars(1_000_000);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }
}
