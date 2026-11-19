package com.pte.itembank.internal.generation.config;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.QuestionGenerationLimit;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchGenerationSettingsTest {

    private static QuestionGenerationProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        binder.bind("itembank.generation", Bindable.ofInstance(properties));
        return properties;
    }

    @Test
    void batchAndPictureAreOffAndTheBatchCapIsFiftyByDefault() {
        QuestionGenerationProperties properties = bind(Map.of());

        assertThat(properties.isBatchEnabled()).isFalse();
        assertThat(properties.isPictureEnabled()).isFalse();
        assertThat(properties.getLimits().getMaxItemsPerBatch())
                .isEqualTo(BatchGenerationConstants.DEFAULT_MAX_ITEMS_PER_BATCH);
    }

    @Test
    void theFlagsAndTheBatchCapBindFromConfiguration() {
        QuestionGenerationProperties properties = bind(Map.of(
                "itembank.generation.batch-enabled", "true",
                "itembank.generation.picture-enabled", "true",
                "itembank.generation.limits.max-items-per-batch", "120"));

        assertThat(properties.isBatchEnabled()).isTrue();
        assertThat(properties.isPictureEnabled()).isTrue();
        assertThat(properties.getLimits().getMaxItemsPerBatch()).isEqualTo(120);
    }

    @Test
    void thePlatformSettingWinsOverTheDeploymentBatchCap() {
        BillingSettingsService settings = mock(BillingSettingsService.class);
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        when(settings.questionGenerationLimit(QuestionGenerationLimit.ITEMS_PER_BATCH, 50)).thenReturn(80);

        assertThat(new GenerationLimits(settings, properties).maxItemsPerBatch()).isEqualTo(80);
    }

    @Test
    void theBatchSettingHasTheAgreedKeyAndRange() {
        assertThat(QuestionGenerationLimit.ITEMS_PER_BATCH.key()).isEqualTo("question_generation_max_items_per_batch");
        assertThat(QuestionGenerationLimit.ITEMS_PER_BATCH.defaultValue()).isEqualTo(50);
        assertThat(QuestionGenerationLimit.ITEMS_PER_BATCH.maximum())
                .isEqualTo(BatchGenerationConstants.MAX_ITEMS_PER_BATCH_HARD_LIMIT);
    }
}
