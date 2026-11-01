package com.pte.chat;

import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiConfig;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.config.ChatGeminiConfig;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.controller.ChatController;
import com.pte.chat.internal.controller.ChatAdminUsageController;
import com.pte.chat.internal.service.report.ChatUsageReportService;
import com.pte.chat.internal.service.usage.ChatUsageDailyRepository;
import com.pte.chat.internal.service.usage.ChatUsageRecorder;
import com.pte.chat.internal.service.ChatObservability;
import com.pte.chat.internal.service.ChatService;
import com.pte.chat.internal.service.quota.ChatQuotaRepository;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.practice.PracticeEntitlementGate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Plan AD-11: with {@code chat.ai.enabled} off, no chat bean exists and the app boots without a key.
 */
class ChatBeanGatingTest {

    private static final List<Class<?>> GATED_BEANS = List.of(
            ChatQuotaRepository.class,
            ChatQuotaService.class,
            ChatObservability.class,
            ChatConcurrencyGate.class,
            ChatUsageDailyRepository.class,
            ChatUsageRecorder.class,
            ChatUsageReportService.class,
            ChatAdminUsageController.class,
            ChatService.class,
            ChatController.class);

    static List<Class<?>> gatedBeans() {
        return GATED_BEANS;
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(GATED_BEANS.toArray(Class<?>[]::new))
                .withUserConfiguration(ChatAiConfig.class, ChatGeminiConfig.class);
    }

    /** Everything the chat beans lean on from the rest of the application, as inert stand-ins. */
    private static ApplicationContextRunner withCollaborators(ApplicationContextRunner runner) {
        return runner
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(PracticeEntitlementGate.class, () -> mock(PracticeEntitlementGate.class))
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatedBeans")
    void everyChatBeanIsConditionalOnTheEnabledSwitch(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).as("@ConditionalOnProperty on %s", type.getSimpleName()).isNotNull();
        assertThat(condition.name()).containsExactly(ChatConstants.PROPERTY_ENABLED);
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatedBeans")
    void everyGatedClassIsASpringStereotypeSoComponentScanFindsIt(Class<?> type) {
        assertThat(AnnotatedElementUtils.hasAnnotation(type, Component.class))
                .as("%s must be a Spring component (@Component/@Service/@Repository/@RestController)", type.getSimpleName())
                .isTrue();
    }

    @Test
    void withTheSwitchAbsentNoChatBeanExistsAndNoKeyIsNeeded() {
        withCollaborators(runner()).run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> type : GATED_BEANS) {
                assertThat(context).as(type.getSimpleName()).doesNotHaveBean(type);
            }
            assertThat(context).doesNotHaveBean(GeminiChatClient.class);
            assertThat(context).doesNotHaveBean(ChatAiProperties.class);
        });
    }

    @Test
    void withTheSwitchFalseNoChatBeanExists() {
        withCollaborators(runner()).withPropertyValues("chat.ai.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> type : GATED_BEANS) {
                assertThat(context).as(type.getSimpleName()).doesNotHaveBean(type);
            }
        });
    }

    @Test
    void anyValueOtherThanTrueKeepsTheBeansOff() {
        withCollaborators(runner()).withPropertyValues("chat.ai.enabled=yes").run(context -> {
            for (Class<?> type : GATED_BEANS) {
                assertThat(context).as(type.getSimpleName()).doesNotHaveBean(type);
            }
        });
    }

    @Test
    void withTheSwitchOnEveryGatedBeanExistsExactlyOnce() {
        withCollaborators(runner()).withPropertyValues("chat.ai.enabled=true", "chat.ai.api-key=AIza-dummy-key_1",
                "chat.ai.model=gemini-2.5-flash-lite").run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> type : GATED_BEANS) {
                assertThat(context).as(type.getSimpleName()).hasSingleBean(type);
            }
        });
    }
}
