package com.pte.itembank.internal.generation.usage;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The daily cap on prompt test runs. Each run calls the real model, so one user may start only so many in a
 * Vietnam day (the admins' calendar, as in {@link GenerationUsageRecorder}). The user is the signed-in caller's
 * id; prompt editing belongs to platform roles, so the cap has no tenant. A run that was counted stays counted
 * even when the provider then fails: the call was still made.
 *
 * <p>The count lives in Redis ({@link PromptTestUsageCounter}). If Redis cannot be reached the run is allowed:
 * the cap guards against waste, and an outage of the counter must not stop the people who maintain the prompts.
 */
@Component
public class PromptTestQuota {

    private static final Logger LOG = LoggerFactory.getLogger(PromptTestQuota.class);

    private final PromptTestUsageCounter counter;
    private final QuestionGenerationProperties properties;
    private final Clock clock;

    public PromptTestQuota(PromptTestUsageCounter counter, QuestionGenerationProperties properties, Clock clock) {
        this.counter = counter;
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one run for the user, or refuses it when today's runs are used up. */
    public void consume(UUID userPublicId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), GenerationUsageRecorder.ZONE);
        long runs;
        try {
            runs = counter.countRun(userPublicId, today);
        } catch (RuntimeException counterFailure) {
            LOG.warn("Prompt test counter unavailable, the run is allowed uncounted: {}", counterFailure.toString());
            return;
        }
        if (runs > properties.getLimits().getMaxPromptTestsPerUserPerDay()) {
            throw new GenerationLimitException(PromptVersionConstants.TEST_DAILY_LIMIT_REACHED,
                    PromptVersionConstants.TEST_DAILY_LIMIT_REACHED_MESSAGE);
        }
    }
}
