package com.pte.chat.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code chat.ai.retention-*}: defaults, binding and validation. The privacy hand-off fixes 12 months and 30 days. */
class ChatRetentionPropertiesTest {

    private static ChatAiProperties validEnabled() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("AIza-test-key_123");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    @Test
    void theDefaultsFollowThePrivacyHandOff() {
        ChatAiProperties properties = new ChatAiProperties();

        assertThat(properties.getRetentionContentMonths()).isEqualTo(12);
        assertThat(properties.getRetentionCacheDays()).isEqualTo(30);
        assertThat(properties.getRetentionBatchSize()).isEqualTo(1000);
    }

    @Test
    void theDefaultsAreValid() {
        assertThat(ChatAiPropertiesValidator.validate(validEnabled())).isEmpty();
    }

    @Test
    void bindsTheKebabCaseNames() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "chat.ai.retention-content-months", "6",
                "chat.ai.retention-cache-days", "7",
                "chat.ai.retention-batch-size", "250")));

        ChatAiProperties bound = binder.bind("chat.ai", ChatAiProperties.class).get();

        assertThat(bound.getRetentionContentMonths()).isEqualTo(6);
        assertThat(bound.getRetentionCacheDays()).isEqualTo(7);
        assertThat(bound.getRetentionBatchSize()).isEqualTo(250);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aNonPositiveContentMonthCountIsReportedByName(int value) {
        assertRejected(ChatAiProperties::setRetentionContentMonths, value, "chat.ai.retention-content-months");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aNonPositiveCacheDayCountIsReportedByName(int value) {
        assertRejected(ChatAiProperties::setRetentionCacheDays, value, "chat.ai.retention-cache-days");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aNonPositiveBatchSizeIsReportedByName(int value) {
        assertRejected(ChatAiProperties::setRetentionBatchSize, value, "chat.ai.retention-batch-size");
    }

    @ParameterizedTest
    @ValueSource(ints = {13, 24, 120})
    void moreThanTwelveMonthsIsRejectedBecauseThePublishedNoticeSaysAtMostTwelve(int months) {
        assertRejected(ChatAiProperties::setRetentionContentMonths, months, "chat.ai.retention-content-months");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    void twelveMonthsOrFewerIsAccepted(int months) {
        ChatAiProperties properties = validEnabled();
        properties.setRetentionContentMonths(months);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    @Test
    void thePricesDefaultToGemini25FlashLiteAndAreValidated() {
        ChatAiProperties properties = new ChatAiProperties();
        assertThat(properties.getPriceInputPerMillionUsd()).isEqualByComparingTo("0.10");
        assertThat(properties.getPriceOutputPerMillionUsd()).isEqualByComparingTo("0.40");

        ChatAiProperties negative = validEnabled();
        negative.setPriceInputPerMillionUsd(new java.math.BigDecimal("-1"));
        assertThat(ChatAiPropertiesValidator.validate(negative))
                .anyMatch(problem -> problem.startsWith("chat.ai.price-input-per-million-usd"));

        ChatAiProperties free = validEnabled();
        free.setPriceOutputPerMillionUsd(java.math.BigDecimal.ZERO);
        assertThat(ChatAiPropertiesValidator.validate(free)).isEmpty();
    }

    @Test
    void disabledChatIsNeverValidated() {
        ChatAiProperties properties = new ChatAiProperties();
        properties.setRetentionContentMonths(0);

        assertThat(ChatAiPropertiesValidator.validate(properties)).isEmpty();
    }

    private static void assertRejected(BiConsumer<ChatAiProperties, Integer> setter, int value, String name) {
        ChatAiProperties properties = validEnabled();
        setter.accept(properties, value);

        assertThat(ChatAiPropertiesValidator.validate(properties)).anyMatch(problem -> problem.startsWith(name));
    }
}
