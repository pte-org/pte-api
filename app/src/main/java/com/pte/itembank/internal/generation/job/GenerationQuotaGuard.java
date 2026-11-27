package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.QuotaScope;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The single place the daily generation quotas are checked and reported (UTC day, requested questions). A limit of
 * exactly 0 is unlimited and skipped; the other limit still applies.
 */
@Component
public class GenerationQuotaGuard {

    private final GenerationJobRepository jobs;
    private final GenerationLimits limits;
    private final Clock clock;

    public GenerationQuotaGuard(GenerationJobRepository jobs, GenerationLimits limits, Clock clock) {
        this.jobs = jobs;
        this.limits = limits;
        this.clock = clock;
    }

    /** Throws the existing {@code *_LIMIT_REACHED} error when {@code count} more questions would pass a limit. */
    public void requireWithin(CurrentUser caller, int count) {
        Instant dayStart = dayStart();
        int userLimit = limits.maxItemsPerUserPerDay();
        if (!GenerationLimits.isUnlimited(userLimit)
                && jobs.sumRequestedSinceByUser(caller.userId(), dayStart) + count > userLimit) {
            throw new GenerationLimitException(QuestionGenerationConstants.GENERATION_DAILY_LIMIT_REACHED,
                    QuestionGenerationConstants.GENERATION_DAILY_LIMIT_REACHED_MESSAGE);
        }
        int globalLimit = limits.maxItemsPerDayGlobal();
        if (!GenerationLimits.isUnlimited(globalLimit) && jobs.sumRequestedSince(dayStart) + count > globalLimit) {
            throw new GenerationLimitException(QuestionGenerationConstants.GENERATION_GLOBAL_LIMIT_REACHED,
                    QuestionGenerationConstants.GENERATION_GLOBAL_LIMIT_REACHED_MESSAGE);
        }
    }

    /** Today's use and what is left under the binding limit (null when both daily limits are unlimited). */
    @Transactional(readOnly = true)
    public GenerationQuotaResponse snapshot(CurrentUser caller) {
        Instant dayStart = dayStart();
        long used = jobs.sumRequestedSinceByUser(caller.userId(), dayStart);
        int userLimit = limits.maxItemsPerUserPerDay();
        int globalLimit = limits.maxItemsPerDayGlobal();
        Long userLeft = GenerationLimits.isUnlimited(userLimit) ? null : Math.max(0, userLimit - used);
        Long globalLeft = GenerationLimits.isUnlimited(globalLimit) ? null
                : Math.max(0, globalLimit - jobs.sumRequestedSince(dayStart));
        QuotaScope scope = null;
        Long remaining = null;
        if (userLeft != null && (globalLeft == null || userLeft <= globalLeft)) {
            scope = QuotaScope.USER;
            remaining = userLeft;
        } else if (globalLeft != null) {
            scope = QuotaScope.GLOBAL;
            remaining = globalLeft;
        }
        return new GenerationQuotaResponse(used, userLimit, remaining == null ? null : remaining.intValue(),
                scope == null, scope, limits.effectiveMaxItemsPerBatch(), limits.pictureEnabled());
    }

    private Instant dayStart() {
        return clock.instant().truncatedTo(ChronoUnit.DAYS);
    }
}
