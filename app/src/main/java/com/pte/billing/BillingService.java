package com.pte.billing;

import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only door other modules use to reach billing. Subscription reads live
 * here so future session code does not reach into billing repositories directly.
 */
@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;

    public BillingService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Returns empty when the key is unknown, tenant-owned elsewhere, expired, or cancelled. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> getActiveSubscription(String licenseKey, UUID tenantId) {
        if (licenseKey == null || tenantId == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return subscriptionRepository
                .findByLicenseKeyAndTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThan(
                        licenseKey, tenantId, SubscriptionStatus.ACTIVE, now, now)
                .map(SubscriptionView::from);
    }

    /** Returns an active, usable subscription owned by the tenant. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> getActiveSubscription(UUID subscriptionPublicId, UUID tenantId) {
        if (subscriptionPublicId == null || tenantId == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return subscriptionRepository.findByPublicIdAndTenantId(subscriptionPublicId, tenantId)
                .filter(subscription -> subscription.isUsableAt(now))
                .map(SubscriptionView::from);
    }

    /**
     * Locks every requested subscription in public-id order. The order is part
     * of the public contract because callers changing a session's lane may
     * arrive with opposite old/new pairs.
     */
    @Transactional
    public List<SubscriptionView> lockSubscriptions(List<UUID> subscriptionPublicIds, UUID tenantId) {
        if (subscriptionPublicIds == null || tenantId == null) {
            return List.of();
        }
        return subscriptionPublicIds.stream()
                .filter(id -> id != null)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(id -> subscriptionRepository.findWithLockByPublicIdAndTenantId(id, tenantId)
                        .map(SubscriptionView::from)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Returns only subscriptions usable at this instant; status alone is not sufficient. */
    @Transactional(readOnly = true)
    public List<SubscriptionView> listActiveSubscriptions(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return subscriptionRepository
                .findByTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanOrderByCreatedAtDesc(
                        tenantId, SubscriptionStatus.ACTIVE, now, now).stream()
                .map(SubscriptionView::from)
                .toList();
    }
}
