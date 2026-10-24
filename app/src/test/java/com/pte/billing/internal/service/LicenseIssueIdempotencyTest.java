package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** License issuance dedupe now lives in the shared idempotency store (scope LICENSE_ISSUE). */
@ExtendWith(MockitoExtension.class)
class LicenseIssueIdempotencyTest {

    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

    @Mock
    private LicenseCodeRepository licenseCodeRepository;

    @Mock
    private ServicePlanRepository planRepository;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    private LicenseCodePersistenceService service;
    private final UUID actor = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LicenseCodePersistenceService(licenseCodeRepository, planRepository, idempotencyKeyService,
                JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String idempotencyKey() {
        return actor + BillingConstants.LICENSE_ISSUE_KEY_SEPARATOR + key;
    }

    private ServicePlan activeExamPlan(UUID planId) {
        ServicePlan plan = new ServicePlan();
        plan.setPublicId(planId);
        plan.setName("Exam");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(BigDecimal.TEN);
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(20);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }

    @Test
    void firstUseSavesTheCodeAndStoresItsIdAsTheReplayResult() {
        UUID planId = UUID.randomUUID();
        LicenseCode code = LicenseCode.issue("PTE-CODE-1", planId, actor, NOW, null);
        code.setPublicId(UUID.randomUUID());
        when(idempotencyKeyService.begin(eq(IdempotencyScope.LICENSE_ISSUE), eq(idempotencyKey()), isNull(),
                eq("fp"), any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
        when(planRepository.findByPublicIdForUpdate(planId)).thenReturn(Optional.of(activeExamPlan(planId)));
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        LicenseIssueReceipt receipt = service.issue(code, key, "fp");

        assertThat(receipt.publicId()).isEqualTo(code.getPublicId());
        assertThat(receipt.replayed()).isFalse();
        verify(idempotencyKeyService).complete(IdempotencyScope.LICENSE_ISSUE, idempotencyKey(), null,
                "{\"licenseCodePublicId\":\"" + code.getPublicId() + "\"}");
    }

    @Test
    void sameKeyAndSameRequestReplaysTheStoredCodeWithoutSavingAnother() {
        UUID planId = UUID.randomUUID();
        LicenseCode stored = LicenseCode.issue("PTE-CODE-1", planId, actor, NOW.minusSeconds(60), null);
        stored.setPublicId(UUID.randomUUID());
        LicenseCode candidate = LicenseCode.issue("PTE-CODE-2", planId, actor, NOW, null);
        when(idempotencyKeyService.begin(eq(IdempotencyScope.LICENSE_ISSUE), eq(idempotencyKey()), isNull(),
                eq("fp"), any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.REPLAY,
                        "{\"licenseCodePublicId\":\"" + stored.getPublicId() + "\"}"));
        when(licenseCodeRepository.findByPublicId(stored.getPublicId())).thenReturn(Optional.of(stored));

        LicenseIssueReceipt receipt = service.issue(candidate, key, "fp");

        assertThat(receipt.publicId()).isEqualTo(stored.getPublicId());
        assertThat(receipt.replayed()).isTrue();
        verify(licenseCodeRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() {
        LicenseCode candidate = LicenseCode.issue("PTE-CODE-2", UUID.randomUUID(), actor, NOW, null);
        when(idempotencyKeyService.begin(eq(IdempotencyScope.LICENSE_ISSUE), eq(idempotencyKey()), isNull(),
                eq("other"), any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null));

        assertThatThrownBy(() -> service.issue(candidate, key, "other"))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REUSED));
        verify(licenseCodeRepository, never()).saveAndFlush(any());
    }
}
