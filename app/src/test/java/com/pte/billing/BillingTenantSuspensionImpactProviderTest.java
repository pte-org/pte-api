package com.pte.billing;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenantSuspensionImpact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingTenantSuspensionImpactProviderTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Test
    void preview_countsDaysLostAndReportsSubscriptionsThatExpireDuringWindow() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        Instant suspensionEnd = now.plusSeconds(10 * 86_400L);
        Subscription expiresSoon = subscription(tenantId, now.plusSeconds(5 * 86_400L));
        Subscription expiresLater = subscription(tenantId, now.plusSeconds(20 * 86_400L));
        when(subscriptionRepository.findUsableForSuspensionImpact(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), eq(now)))
                .thenReturn(List.of(expiresSoon, expiresLater));

        TenantSuspensionImpact impact = new BillingTenantSuspensionImpactProvider(subscriptionRepository)
                .preview(tenantId, now, suspensionEnd);

        assertThat(impact.activeSubscriptionCount()).isEqualTo(2);
        assertThat(impact.suspensionDays()).isEqualTo(10);
        assertThat(impact.totalDaysLost()).isEqualTo(15);
        assertThat(impact.totalLossSeconds()).isEqualTo(15 * 86_400L);
        assertThat(impact.remainingSubscriptionCount()).isEqualTo(1);
        assertThat(impact.impactDigest()).isNotBlank();
        assertThat(impact.expiringSubscriptions()).extracting(TenantSuspensionImpact.SubscriptionImpact::subscriptionPublicId)
                .containsExactly(expiresSoon.getPublicId());
        assertThat(impact.expiringSubscriptions().get(0).daysLost()).isEqualTo(5);
        assertThat(impact.expiringSubscriptions().get(0).lossSeconds()).isEqualTo(5 * 86_400L);
    }

    @Test
    void preview_indefiniteSuspensionReportsAllRemainingSubscriptionDays() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        Subscription subscription = subscription(tenantId, now.plusSeconds(4 * 86_400L));
        when(subscriptionRepository.findUsableForSuspensionImpact(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), eq(now)))
                .thenReturn(List.of(subscription));

        TenantSuspensionImpact impact = new BillingTenantSuspensionImpactProvider(subscriptionRepository)
                .preview(tenantId, now, null);

        assertThat(impact.suspensionDays()).isZero();
        assertThat(impact.suspendedUntil()).isNull();
        assertThat(impact.totalDaysLost()).isEqualTo(4);
        assertThat(impact.totalLossSeconds()).isEqualTo(4 * 86_400L);
        assertThat(impact.remainingSubscriptionCount()).isZero();
        assertThat(impact.expiringSubscriptions()).hasSize(1);
    }

    private Subscription subscription(UUID tenantId, Instant expiresAt) {
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        subscription.setTenantId(tenantId);
        subscription.setPlanId(UUID.randomUUID());
        subscription.setStartsAt(Instant.parse("2026-10-01T00:00:00Z"));
        subscription.setExpiresAt(expiresAt);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setDeleted(false);
        return subscription;
    }
}
