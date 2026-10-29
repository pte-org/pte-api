package com.pte.chat.internal.config;

import com.pte.chat.internal.client.GeminiChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thinking tokens count against {@code max-tokens}, so only the Flash-Lite 2.5 family (thinking off by default)
 * may be configured until a verified thinking level is sent. Any other model stops startup with a clear reason.
 */
class ChatGeminiModelGuardTest {

    private static final String KEY = "AIza-SECRET-KEY-7f3a91";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(ChatAiConfig.class, ChatGeminiConfig.class);

    private ApplicationContextRunner enabledWith(String model) {
        return runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=" + KEY, "chat.ai.model=" + model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini-2.5-flash-lite", "gemini-2.5-flash-lite-preview-09-2025"})
    void theFlashLiteTwoPointFiveFamilyCreatesTheClient(String model) {
        enabledWith(model).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GeminiChatClient.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini-2.5-flash", "gemini-3.5-flash-lite", "gemini-1.5-flash"})
    void anyOtherModelStopsStartupNamingTheModelAndTheSupportedFamily(String model) {
        enabledWith(model).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining(model)
                    .hasMessageContaining("gemini-2.5-flash-lite");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini-2.5-flash", "gemini-3.5-flash-lite", "gemini-1.5-flash"})
    void theStartupFailureNeverContainsTheApiKey(String model) {
        enabledWith(model).run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            while (failure != null) {
                assertThat(String.valueOf(failure.getMessage())).doesNotContain(KEY);
                failure = failure.getCause();
            }
        });
    }

    @Test
    void aDisabledChatNeverChecksTheModel() {
        runner.withPropertyValues("chat.ai.enabled=false", "chat.ai.model=gemini-1.5-flash").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GeminiChatClient.class);
        });
    }
}
