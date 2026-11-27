package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.QuotaScope;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationQuotaGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-11-18T00:00:00Z");

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationLimits limits = mock(GenerationLimits.class);
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final GenerationQuotaGuard guard = new GenerationQuotaGuard(jobs, limits,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private void limits(int user, int global, int batchCap) {
        when(limits.maxItemsPerUserPerDay()).thenReturn(user);
        when(limits.maxItemsPerDayGlobal()).thenReturn(global);
        when(limits.effectiveMaxItemsPerBatch()).thenReturn(batchCap);
    }

    private void used(long byUser, long global) {
        when(jobs.sumRequestedSinceByUser(eq(staff.userId()), eq(DAY_START))).thenReturn(byUser);
        when(jobs.sumRequestedSince(any())).thenReturn(global);
    }

    @Test
    void allLimitsZeroAcceptSeventyItemsAndReportUnlimited() {
        limits(0, 0, 500);
        used(400, 9000);

        assertThatCode(() -> guard.requireWithin(staff, 70)).doesNotThrowAnyException();
        assertThat(guard.snapshot(staff)).isEqualTo(new GenerationQuotaResponse(400, 0, null, true, null, 500, false));
    }

    @Test
    void regressionAUserLimitOfFiveRejectsSixItems() {
        limits(5, 300, 50);
        used(0, 0);

        assertThatThrownBy(() -> guard.requireWithin(staff, 6)).isInstanceOf(GenerationLimitException.class)
                .extracting("code").isEqualTo(QuestionGenerationConstants.GENERATION_DAILY_LIMIT_REACHED);
    }

    @Test
    void theGlobalLimitStillAppliesWhenTheUserLimitIsUnlimited() {
        limits(0, 10, 50);
        used(0, 8);

        assertThatThrownBy(() -> guard.requireWithin(staff, 3)).isInstanceOf(GenerationLimitException.class)
                .extracting("code").isEqualTo(QuestionGenerationConstants.GENERATION_GLOBAL_LIMIT_REACHED);
    }

    @Test
    void theTighterGlobalLimitBindsTheSnapshot() {
        limits(100, 10, 50);
        used(4, 7);

        GenerationQuotaResponse quota = guard.snapshot(staff);

        assertThat(quota.scope()).isEqualTo(QuotaScope.GLOBAL);
        assertThat(quota.remaining()).isEqualTo(3);
        assertThat(quota.unlimited()).isFalse();
    }

    @Test
    void anUnlimitedGlobalLimitLeavesTheUserScope() {
        limits(5, 0, 50);
        used(2, 900);

        GenerationQuotaResponse quota = guard.snapshot(staff);

        assertThat(quota.scope()).isEqualTo(QuotaScope.USER);
        assertThat(quota.remaining()).isEqualTo(3);
        assertThat(quota.maxItemsPerBatch()).isEqualTo(50);
    }

    @Test
    void remainingNeverGoesNegative() {
        limits(5, 0, 50);
        used(9, 9);

        assertThat(guard.snapshot(staff).remaining()).isZero();
    }
}
