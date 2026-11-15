package com.pte.itembank.internal.generation.config;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.QuestionGenerationLimit;
import org.springframework.stereotype.Component;

/**
 * The generation limits in force. A platform admin edits them on the AI overview page (platform settings); until a
 * setting exists, the deployment value ({@code QUESTION_GEN_*}) applies. Read on every use, so a change needs no restart.
 */
@Component
public class GenerationLimits {

    private final BillingSettingsService settings;
    private final QuestionGenerationProperties properties;

    public GenerationLimits(BillingSettingsService settings, QuestionGenerationProperties properties) {
        this.settings = settings;
        this.properties = properties;
    }

    public int maxItemsPerJob() {
        return value(QuestionGenerationLimit.ITEMS_PER_JOB, properties.getLimits().getMaxItemsPerJob());
    }

    public int maxItemsPerUserPerDay() {
        return value(QuestionGenerationLimit.ITEMS_PER_USER_PER_DAY, properties.getLimits().getMaxItemsPerUserPerDay());
    }

    public int maxItemsPerDayGlobal() {
        return value(QuestionGenerationLimit.ITEMS_PER_DAY, properties.getLimits().getMaxItemsPerDayGlobal());
    }

    public int maxConcurrentJobs() {
        return value(QuestionGenerationLimit.CONCURRENT_JOBS, properties.getLimits().getMaxConcurrentJobs());
    }

    public int maxCallsPerItem() {
        return value(QuestionGenerationLimit.CALLS_PER_ITEM, properties.getLimits().getMaxCallsPerItem());
    }

    private int value(QuestionGenerationLimit limit, int fallback) {
        return settings.questionGenerationLimit(limit, fallback);
    }
}
