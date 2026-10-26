package com.pte.billing.internal.service.license;

import com.pte.billing.internal.service.license.LicenseCodePersistenceService;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LicenseCodePersistenceServiceTest {
    @Test void authoritativeEligibilityIsCheckedInsideInsertTransaction() {
        ServicePlanRepository plans = mock(ServicePlanRepository.class);
        LicenseCodeRepository codes = mock(LicenseCodeRepository.class);
        com.pte.shared.idempotency.IdempotencyKeyService idempotency =
                mock(com.pte.shared.idempotency.IdempotencyKeyService.class);
        when(idempotency.begin(any(), anyString(), any(), anyString(), any())).thenReturn(
                new com.pte.shared.idempotency.IdempotencyDecision(
                        com.pte.shared.idempotency.IdempotencyOutcome.NEW, null));
        LicenseCodePersistenceService service = new LicenseCodePersistenceService(codes, plans,
                idempotency, tools.jackson.databind.json.JsonMapper.builder().build(), com.pte.TestClocks.fixed());
        UUID planId = UUID.randomUUID();
        ServicePlan plan = new ServicePlan();
        plan.setStatus(PlanStatus.ARCHIVED);
        when(plans.findByPublicIdForUpdate(planId)).thenReturn(Optional.of(plan));
        LicenseCode code = LicenseCode.issue("FIXTURE-CODE", planId, UUID.randomUUID(), Instant.now(), null);
        assertThatThrownBy(() -> service.issue(code, UUID.randomUUID(), "0".repeat(64))).isInstanceOf(LicenseCodeException.class);
        verifyNoInteractions(codes);
    }
}
