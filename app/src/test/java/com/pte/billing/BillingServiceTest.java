package com.pte.billing;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Test
    void getActiveSubscriptionByPublicId_scopesToTenantAndChecksUsableWindow() {
        BillingService service = new BillingService(subscriptionRepository, planRepository);
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, tenantId, SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByPublicIdAndTenantId(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));

        Optional<SubscriptionView> result = service.getActiveSubscription(subscriptionId, tenantId);

        assertThat(result).isPresent().get().extracting(SubscriptionView::publicId).isEqualTo(subscriptionId);
    }

    @Test
    void getSubscriptionByPublicIdReturnsInactiveTenantOwnedSubscription() {
        BillingService service = new BillingService(subscriptionRepository, planRepository);
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, tenantId, SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findByPublicIdAndTenantId(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));

        Optional<SubscriptionView> result = service.getSubscription(subscriptionId, tenantId);

        assertThat(result).isPresent().get().extracting(SubscriptionView::status)
                .isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void lockSubscriptions_usesAscendingPublicIdOrder() {
        BillingService service = new BillingService(subscriptionRepository, planRepository);
        UUID tenantId = UUID.randomUUID();
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(subscriptionRepository.findWithLockByPublicIdAndTenantId(low, tenantId))
                .thenReturn(Optional.of(subscription(low, tenantId, SubscriptionStatus.ACTIVE)));
        when(subscriptionRepository.findWithLockByPublicIdAndTenantId(high, tenantId))
                .thenReturn(Optional.of(subscription(high, tenantId, SubscriptionStatus.ACTIVE)));

        List<SubscriptionView> result = service.lockSubscriptions(List.of(high, low), tenantId);

        assertThat(result).extracting(SubscriptionView::publicId).containsExactly(low, high);
        InOrder order = inOrder(subscriptionRepository);
        order.verify(subscriptionRepository).findWithLockByPublicIdAndTenantId(eq(low), eq(tenantId));
        order.verify(subscriptionRepository).findWithLockByPublicIdAndTenantId(eq(high), eq(tenantId));
    }

    @Test
    void activeExamPackageIsDifferentFromCapacityOnlyPlan() {
        BillingService service = new BillingService(subscriptionRepository, planRepository);
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Subscription subscription = subscription(UUID.randomUUID(), tenantId, SubscriptionStatus.ACTIVE);
        subscription.setPlanId(planId);
        Plan plan = new Plan();
        plan.setPublicId(planId);
        plan.setType(PlanType.EXAM_PACKAGE);
        when(subscriptionRepository
                .findByTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanOrderByCreatedAtDesc(
                        eq(tenantId), eq(SubscriptionStatus.ACTIVE), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(subscription));
        when(planRepository.findByPublicIdIn(java.util.Set.of(planId))).thenReturn(List.of(plan));

        assertThat(service.hasActiveExamPackageSubscription(tenantId)).isTrue();
    }

    private Subscription subscription(UUID publicId, UUID tenantId, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setPublicId(publicId);
        subscription.setTenantId(tenantId);
        subscription.setPlanId(UUID.randomUUID());
        subscription.setLicenseKey("LIC-" + publicId);
        subscription.setStartsAt(Instant.now().minusSeconds(60));
        subscription.setExpiresAt(Instant.now().plusSeconds(3600));
        subscription.setMaxStudentsPerSession(200);
        subscription.setStatus(status);
        subscription.setActivationSource(ActivationSource.LICENSE_CODE);
        return subscription;
    }
}
