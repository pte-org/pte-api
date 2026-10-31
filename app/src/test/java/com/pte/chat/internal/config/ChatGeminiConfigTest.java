package com.pte.chat.internal.config;

import com.pte.chat.internal.client.GeminiChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGeminiConfigTest {

    private static final String KEY = "AIza-test-key_123";
    private static final String MODEL = "gemini-2.5-flash-lite";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(ChatAiConfig.class, ChatGeminiConfig.class);

    @Test
    void disabledByDefaultCreatesNoClientAndStartsWithoutAnyKey() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GeminiChatClient.class);
        });
    }

    @Test
    void explicitlyDisabledCreatesNoClientEvenWithGarbageSettings() {
        runner.withPropertyValues("chat.ai.enabled=false", "chat.ai.api-key= bad key ",
                "chat.ai.model=x-latest", "chat.ai.temperature=9").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GeminiChatClient.class);
        });
    }

    @Test
    void enabledWithAKeyAndAPinnedModelCreatesTheClient() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=" + KEY, "chat.ai.model=" + MODEL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GeminiChatClient.class);
                });
    }

    @Test
    void enabledWithCustomTuningStillCreatesTheClient() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=" + KEY, "chat.ai.model=" + MODEL,
                "chat.ai.temperature=0.5", "chat.ai.max-tokens=400", "chat.ai.connect-timeout-ms=1500",
                "chat.ai.read-timeout-ms=15000").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GeminiChatClient.class);
        });
    }

    @Test
    void enabledWithALatestAliasModelFailsStartup() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=" + KEY,
                "chat.ai.model=gemini-flash-lite-latest").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("latest");
        });
    }

    @Test
    void enabledWithABlankKeyFailsStartup() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=   ", "chat.ai.model=" + MODEL)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("chat.ai.api-key");
                });
    }

    @Test
    void enabledWithoutAKeyFailsStartup() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.model=" + MODEL).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("chat.ai.api-key");
        });
    }

    @Test
    void startupFailureListsEveryProblemAtOnce() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=   ",
                "chat.ai.model=gemini-flash-latest", "chat.ai.temperature=3.5", "chat.ai.read-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("chat.ai.api-key")
                            .hasMessageContaining("latest")
                            .hasMessageContaining("temperature")
                            .hasMessageContaining("read-timeout-ms");
                });
    }

    @Test
    void startupFailureNeverEchoesTheConfiguredKey() {
        runner.withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=" + KEY,
                "chat.ai.model=gemini-flash-latest").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageNotContaining(KEY);
        });
    }
}
