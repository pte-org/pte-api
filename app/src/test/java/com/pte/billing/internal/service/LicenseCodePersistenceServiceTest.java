package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.exception.LicenseCodeException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LicenseCodePersistenceServiceTest {
    @Test void authoritativeEligibilityIsCheckedInsideInsertTransaction() {
        PlanRepository plans = mock(PlanRepository.class);
        LicenseCodeRepository codes = mock(LicenseCodeRepository.class);
        LicenseCodePersistenceService service = new LicenseCodePersistenceService(codes, plans,
                mock(com.pte.billing.internal.repository.LicenseIssueIntentRepository.class), java.time.Clock.systemUTC());
        UUID planId = UUID.randomUUID();
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.ARCHIVED);
        when(plans.findByPublicIdForUpdate(planId)).thenReturn(Optional.of(plan));
        LicenseCode code = LicenseCode.issue("FIXTURE-CODE", planId, UUID.randomUUID(), Instant.now(), null);
        assertThatThrownBy(() -> service.issue(code, UUID.randomUUID(), "0".repeat(64))).isInstanceOf(LicenseCodeException.class);
        verifyNoInteractions(codes);
    }
}
