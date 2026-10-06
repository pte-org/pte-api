package com.pte.billing.internal.service;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionActivationServiceTest {

    @Mock
    private SubscriptionPersistenceService persistence;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private LicenseKeyGenerator keyGenerator;

    @Mock
    private TenancyService tenancyService;

    private SubscriptionActivationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionActivationService(persistence, subscriptionRepository,
                keyGenerator, tenancyService);
    }

    @Test
    void activateExamPackageReturnsExactSubscriptionTarget() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Plan plan = examPlan(planId);
        when(keyGenerator.generate(any(Plan.class), any(Instant.class))).thenReturn("PTE-EXAM-2026-ABC123");
        when(subscriptionRepository.existsByLicenseKey("PTE-EXAM-2026-ABC123")).thenReturn(false);
        when(persistence.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setPublicId(subscriptionId);
            return subscription;
        });

        SubscriptionActivationResponse result = service.activate(tenantId, plan, ActivationSource.PAYMENT);

        assertThat(result.kind()).isEqualTo(SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION);
        assertThat(result.targetPublicId()).isEqualTo(subscriptionId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.activationSource()).isEqualTo(ActivationSource.PAYMENT.name());
    }

    @Test
    void activateStudentCapacityReturnsExactQuotaLedgerTarget() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID quotaTransactionId = UUID.randomUUID();
        Plan plan = capacityPlan(planId);
        when(tenancyService.grantQuota(tenantId, 40,
                "Activated STUDENT_CAPACITY plan " + planId)).thenReturn(quotaTransactionId);

        SubscriptionActivationResponse result = service.activate(tenantId, plan, ActivationSource.LICENSE_CODE);

        assertThat(result.kind()).isEqualTo(SubscriptionActivationResponse.ActivationKind.STUDENT_CAPACITY);
        assertThat(result.targetPublicId()).isEqualTo(quotaTransactionId);
        assertThat(result.grantedStudentSlots()).isEqualTo(40);
    }

    private Plan examPlan(UUID planId) {
        Plan plan = new Plan();
        plan.setPublicId(planId);
        plan.setName("Exam");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(new BigDecimal("50000.00"));
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(25);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }

    private Plan capacityPlan(UUID planId) {
        Plan plan = new Plan();
        plan.setPublicId(planId);
        plan.setName("Capacity");
        plan.setType(PlanType.STUDENT_CAPACITY);
        plan.setPrice(new BigDecimal("10000.00"));
        plan.setCurrency("VND");
        plan.setExtraStudentSlots(40);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }
}
