package com.pte.itembank.internal.generation.config;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.QuestionGenerationLimit;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationLimitsTest {

    private final BillingSettingsService settings = mock(BillingSettingsService.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final GenerationLimits limits = new GenerationLimits(settings, properties);

    @Test
    void onlyExactlyZeroIsUnlimited() {
        assertThat(GenerationLimits.isUnlimited(0)).isTrue();
        assertThat(GenerationLimits.isUnlimited(1)).isFalse();
        assertThat(GenerationLimits.isUnlimited(-1)).isFalse();
    }

    @Test
    void aZeroSettingIsPassedThroughAsUnlimited() {
        when(settings.questionGenerationLimit(eq(QuestionGenerationLimit.ITEMS_PER_USER_PER_DAY), anyInt()))
                .thenReturn(0);

        assertThat(GenerationLimits.isUnlimited(limits.maxItemsPerUserPerDay())).isTrue();
    }

    @Test
    void anUnlimitedBatchFallsBackToTheHardCap() {
        when(settings.questionGenerationLimit(eq(QuestionGenerationLimit.ITEMS_PER_BATCH), anyInt())).thenReturn(0);

        assertThat(limits.effectiveMaxItemsPerBatch())
                .isEqualTo(BatchGenerationConstants.MAX_ITEMS_PER_BATCH_HARD_LIMIT);
    }

    @Test
    void aLimitedBatchKeepsItsValue() {
        when(settings.questionGenerationLimit(eq(QuestionGenerationLimit.ITEMS_PER_BATCH), anyInt())).thenReturn(50);

        assertThat(limits.effectiveMaxItemsPerBatch()).isEqualTo(50);
    }

    @Test
    void theCodeDefaultsAreUnchanged() {
        QuestionGenerationProperties.Limits defaults = properties.getLimits();

        assertThat(defaults.getMaxItemsPerUserPerDay()).isEqualTo(100);
        assertThat(defaults.getMaxItemsPerDayGlobal()).isEqualTo(300);
        assertThat(defaults.getMaxItemsPerBatch()).isZero();
        assertThat(properties.isBatchEnabled()).isFalse();
    }
}
