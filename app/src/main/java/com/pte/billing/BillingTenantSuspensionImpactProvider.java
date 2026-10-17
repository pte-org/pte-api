package com.pte.billing;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.TenantSuspensionImpactProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Billing implementation of the tenancy suspension impact read boundary. */
@Service
public class BillingTenantSuspensionImpactProvider implements TenantSuspensionImpactProvider {

    private final SubscriptionRepository subscriptionRepository;

    public BillingTenantSuspensionImpactProvider(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public TenantSuspensionImpact preview(UUID tenantPublicId, Instant now, Instant suspendedUntil) {
        List<Subscription> active = subscriptionRepository.findUsableForSuspensionImpact(
                tenantPublicId, SubscriptionStatus.ACTIVE, now);
        List<TenantSuspensionImpact.SubscriptionImpact> expiring = active.stream()
                .filter(subscription -> suspendedUntil == null
                        || !subscription.getExpiresAt().isAfter(suspendedUntil))
                .map(subscription -> new TenantSuspensionImpact.SubscriptionImpact(
                        subscription.getPublicId(), subscription.getPlanId(), subscription.getExpiresAt(),
                        daysLost(now, subscription.getExpiresAt(), suspendedUntil)))
                .toList();
        long totalDaysLost = active.stream()
                .mapToLong(subscription -> daysLost(now, subscription.getExpiresAt(), suspendedUntil))
                .sum();
        int suspensionDays = suspendedUntil == null ? 0 : Math.toIntExact(ChronoUnit.DAYS.between(now, suspendedUntil));
        return new TenantSuspensionImpact(tenantPublicId, suspensionDays, suspendedUntil, active.size(),
                totalDaysLost, expiring);
    }

    private long daysLost(Instant now, Instant expiresAt, Instant suspendedUntil) {
        Instant lossEnd = suspendedUntil == null || expiresAt.isBefore(suspendedUntil)
                ? expiresAt : suspendedUntil;
        return Math.max(0L, Duration.between(now, lossEnd).toDays());
    }
}
