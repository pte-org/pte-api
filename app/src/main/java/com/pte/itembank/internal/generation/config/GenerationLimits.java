package com.pte.itembank.internal.generation.config;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.QuestionGenerationLimit;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import org.springframework.stereotype.Component;

/**
 * The generation limits in force. A platform admin edits them on the AI overview page (platform settings); until a
 * setting exists, the deployment value ({@code QUESTION_GEN_*}) applies. Read on every use, so a change needs no restart.
 * For the batch size and the two daily limits, exactly {@code 0} means unlimited ({@link #isUnlimited(int)}).
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

    public int maxItemsPerBatch() {
        return value(QuestionGenerationLimit.ITEMS_PER_BATCH, properties.getLimits().getMaxItemsPerBatch());
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

    /** Hours a paused batch is kept before it is cancelled; 0 (unlimited) keeps it. */
    public int pausedBatchExpiryHours() {
        return value(QuestionGenerationLimit.PAUSED_BATCH_EXPIRY_HOURS,
                properties.getLimits().getPausedBatchExpiryHours());
    }

    /** The batch cap in force: the batch limit, or the hard cap when it is unlimited (0). */
    public int effectiveMaxItemsPerBatch() {
        int configured = maxItemsPerBatch();
        return isUnlimited(configured) ? BatchGenerationConstants.MAX_ITEMS_PER_BATCH_HARD_LIMIT
                : Math.min(configured, BatchGenerationConstants.MAX_ITEMS_PER_BATCH_HARD_LIMIT);
    }

    /** The one "0 = unlimited" rule of generation limits. */
    public static boolean isUnlimited(int limit) {
        return limit == QuestionGenerationConstants.UNLIMITED;
    }

    private int value(QuestionGenerationLimit limit, int fallback) {
        return settings.questionGenerationLimit(limit, fallback);
    }

    /** AI-painted Describe Image pictures are on in this deployment; when off, only charts are drawn. */
    public boolean pictureEnabled() {
        return properties.isPictureEnabled();
    }
}
