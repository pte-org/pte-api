package com.pte.chat.internal.config;

import com.pte.chat.internal.client.ChatAiClient;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.client.OpenAiChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSharedProviderConfigTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("application",
                                        new FileSystemResource("src/main/resources/application.yml"))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .withUserConfiguration(ChatAiConfig.class, ChatGeminiConfig.class, ChatOpenAiConfig.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build());
    }

    @Test
    void enabledChatUsesOnlyQuestionGenerationCredentialsEvenWhenGenerationIsDisabled() {
        runner().withPropertyValues("CHAT_AI_ENABLED=true", "QUESTION_GEN_ENABLED=false",
                "QUESTION_GEN_API_KEY=fake-shared-key", "QUESTION_GEN_BASE_URL=http://localhost:9999/v1",
                "QUESTION_GEN_GENERATOR_MODEL=Capstone", "QUESTION_GEN_MAX_TOKENS=0",
                "CHAT_AI_GEMINI_API_KEY=obsolete-key", "CHAT_AI_MODEL=obsolete-model")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatAiClient.class)
                            .hasSingleBean(OpenAiChatClient.class).doesNotHaveBean(GeminiChatClient.class);
                    ChatAiProperties properties = context.getBean(ChatAiProperties.class);
                    assertThat(properties.getApiKey()).isEqualTo("fake-shared-key");
                    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:9999/v1");
                    assertThat(properties.getModel()).isEqualTo("Capstone");
                    assertThat(properties.getMaxTokens()).isZero();
                });
    }

    @Test
    void theSharedAiGatewayNamesWinOverTheOldQuestionGenerationNames() {
        runner().withPropertyValues("CHAT_AI_ENABLED=true", "AI_GATEWAY_API_KEY=gateway-key",
                        "AI_GATEWAY_BASE_URL=http://gateway:9999/v1", "AI_GATEWAY_MODEL=Capstone",
                        "QUESTION_GEN_API_KEY=old-key", "QUESTION_GEN_BASE_URL=http://old:1/v1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenAiChatClient.class);
                    ChatAiProperties properties = context.getBean(ChatAiProperties.class);
                    assertThat(properties.getApiKey()).isEqualTo("gateway-key");
                    assertThat(properties.getBaseUrl()).isEqualTo("http://gateway:9999/v1");
                });
    }

    @Test
    void disabledChatDoesNotNeedSharedCredentials() {
        runner().withPropertyValues("CHAT_AI_ENABLED=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ChatAiClient.class)
                    .doesNotHaveBean(ChatAiProperties.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"vendor/model:free", "combo-latest", "My Combo"})
    void acceptsTheSameModelNamesAsQuestionGeneration(String model) {
        runner().withPropertyValues("CHAT_AI_ENABLED=true", "QUESTION_GEN_API_KEY=fake-shared-key",
                "QUESTION_GEN_BASE_URL=http://localhost:9999/v1", "QUESTION_GEN_GENERATOR_MODEL=" + model)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenAiChatClient.class);
                    assertThat(context.getBean(ChatAiProperties.class).getModel()).isEqualTo(model);
                });
    }
}
