package com.pte.billing;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.TenantSuspensionImpactProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Billing implementation of the tenancy suspension impact read boundary. */
@Service
public class BillingTenantSuspensionImpactProvider implements TenantSuspensionImpactProvider {

    private final SubscriptionRepository subscriptionRepository;
    private final ServicePlanRepository planRepository;

    public BillingTenantSuspensionImpactProvider(SubscriptionRepository subscriptionRepository) {
        this(subscriptionRepository, null);
    }

    @Autowired
    public BillingTenantSuspensionImpactProvider(SubscriptionRepository subscriptionRepository,
            ServicePlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @Override
    public TenantSuspensionImpact preview(UUID tenantPublicId, Instant now, Instant suspendedUntil) {
        List<Subscription> active = subscriptionRepository.findUsableForSuspensionImpact(
                tenantPublicId, SubscriptionStatus.ACTIVE, now);
        Map<UUID, String> planLabels = planLabels(active);
        List<TenantSuspensionImpact.SubscriptionImpact> expiring = active.stream()
                .filter(subscription -> suspendedUntil == null
                        || !subscription.getExpiresAt().isAfter(suspendedUntil))
                .map(subscription -> new TenantSuspensionImpact.SubscriptionImpact(
                        subscription.getPublicId(), subscription.getPlanId(), subscription.getExpiresAt(),
                        lossSeconds(now, subscription.getExpiresAt(), suspendedUntil) / 86_400L,
                        lossSeconds(now, subscription.getExpiresAt(), suspendedUntil),
                        planLabels.get(subscription.getPlanId())))
                .toList();
        long totalLossSeconds = active.stream()
                .mapToLong(subscription -> lossSeconds(now, subscription.getExpiresAt(), suspendedUntil))
                .sum();
        long totalDaysLost = totalLossSeconds / 86_400L;
        int suspensionDays = suspendedUntil == null ? 0 : Math.toIntExact(ChronoUnit.DAYS.between(now, suspendedUntil));
        return new TenantSuspensionImpact(tenantPublicId, suspensionDays, suspendedUntil, active.size(),
                totalDaysLost, expiring, totalLossSeconds, active.size() - expiring.size(),
                digest(tenantPublicId, suspensionDays, suspendedUntil, active));
    }

    private long lossSeconds(Instant now, Instant expiresAt, Instant suspendedUntil) {
        Instant lossEnd = suspendedUntil == null || expiresAt.isBefore(suspendedUntil)
                ? expiresAt : suspendedUntil;
        return Math.max(0L, Duration.between(now, lossEnd).getSeconds());
    }

    private Map<UUID, String> planLabels(List<Subscription> subscriptions) {
        if (planRepository == null || subscriptions.isEmpty()) {
            return Map.of();
        }
        List<UUID> planIds = subscriptions.stream()
                .map(Subscription::getPlanId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return planRepository.findByPublicIdIn(planIds).stream()
                .collect(Collectors.toUnmodifiableMap(ServicePlan::getPublicId, ServicePlan::getName, (first, ignored) -> first));
    }

    private String digest(UUID tenantPublicId, int suspensionDays, Instant suspendedUntil,
            List<Subscription> active) {
        String canonical = tenantPublicId + "|"
                + (suspendedUntil == null ? "INDEFINITE" : Integer.toString(suspensionDays)) + "|"
                + active.stream()
                        .sorted(Comparator.comparing(Subscription::getPublicId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .map(subscription -> String.valueOf(subscription.getPublicId()) + ":"
                                + String.valueOf(subscription.getPlanId()) + ":"
                                + String.valueOf(subscription.getExpiresAt()))
                        .collect(Collectors.joining("|"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for suspension impact confirmation", exception);
        }
    }
}
