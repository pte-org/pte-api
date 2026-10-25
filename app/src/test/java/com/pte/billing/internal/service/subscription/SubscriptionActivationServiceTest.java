package com.pte.billing.internal.service.subscription;

import com.pte.billing.internal.service.license.LicenseKeyGenerator;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.internal.service.subscription.SubscriptionPersistenceService;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                keyGenerator, tenancyService, java.time.Clock.systemUTC());
    }

    @Test
    void activateExamPackageReturnsExactSubscriptionTarget() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ServicePlan plan = examPlan(planId);
        when(keyGenerator.generate(any(ServicePlan.class), any(Instant.class))).thenReturn("PTE-EXAM-2026-ABC123");
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
    void activateExamPackageStartsIndependentExpiryWithoutChangingSessionCap() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ServicePlan plan = examPlan(planId);
        Instant existingExpiry = Instant.now().plus(Duration.ofDays(12));
        Subscription existing = new Subscription();
        existing.setExpiresAt(existingExpiry);
        when(keyGenerator.generate(any(ServicePlan.class), any(Instant.class))).thenReturn("PTE-EXAM-2026-RENEW");
        when(subscriptionRepository.existsByLicenseKey("PTE-EXAM-2026-RENEW")).thenReturn(false);
        when(persistence.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionActivationResponse result = service.activate(tenantId, plan, ActivationSource.PAYMENT);

        assertThat(result.expiresAt()).isAfter(existingExpiry.plus(Duration.ofDays(17)));
        assertThat(result.expiresAt()).isBefore(existingExpiry.plus(Duration.ofDays(19)));
        assertThat(result.maxStudentsPerSession()).isEqualTo(plan.getMaxStudentsPerSession());
        verify(subscriptionRepository, never()).findUsableForPlanRenewal(any(UUID.class), any(UUID.class),
                any(SubscriptionStatus.class), any(Instant.class));
    }

    @Test
    void activateStudentCapacityReturnsExactQuotaLedgerTarget() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID quotaGrantId = UUID.randomUUID();
        ServicePlan plan = capacityPlan(planId);
        when(tenancyService.grantQuota(tenantId, 40,
                "Activated STUDENT_CAPACITY plan " + planId)).thenReturn(quotaGrantId);

        SubscriptionActivationResponse result = service.activate(tenantId, plan, ActivationSource.LICENSE_CODE);

        assertThat(result.kind()).isEqualTo(SubscriptionActivationResponse.ActivationKind.STUDENT_CAPACITY);
        assertThat(result.targetPublicId()).isEqualTo(quotaGrantId);
        assertThat(result.grantedStudentSlots()).isEqualTo(40);
    }

    private ServicePlan examPlan(UUID planId) {
        ServicePlan plan = new ServicePlan();
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

    private ServicePlan capacityPlan(UUID planId) {
        ServicePlan plan = new ServicePlan();
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
