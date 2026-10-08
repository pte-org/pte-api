package com.pte.billing;

import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach billing. Subscription reads live
 * here so future session code does not reach into billing repositories directly.
 */
@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public BillingService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            AuditLogService auditLogService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.auditLogService = auditLogService;
    }

    /** Compatibility constructor for focused billing tests. */
    public BillingService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this(subscriptionRepository, planRepository, null);
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
                .filter(subscription -> !subscription.isDeleted())
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
                .filter(subscription -> !subscription.isDeleted() && subscription.isUsableAt(now))
                .map(SubscriptionView::from);
    }

    /** Returns an exact tenant-owned subscription, including expired/cancelled state. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> getSubscription(UUID subscriptionPublicId, UUID tenantId) {
        if (subscriptionPublicId == null || tenantId == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findByPublicIdAndTenantId(subscriptionPublicId, tenantId)
                .filter(subscription -> !subscription.isDeleted())
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
                .filter(subscription -> !subscription.isDeleted())
                .map(SubscriptionView::from)
                .toList();
    }

    /** Platform-wide masked subscription projection for operations staff. */
    @Transactional(readOnly = true)
    public PagedResult<SubscriptionView> listPlatformSubscriptions(CurrentUser caller, int requestedPage,
            int requestedSize) {
        return listPlatformSubscriptions(caller, requestedPage, requestedSize, null);
    }

    /** Platform-wide masked subscription projection with an optional tenant filter. */
    @Transactional(readOnly = true)
    public PagedResult<SubscriptionView> listPlatformSubscriptions(CurrentUser caller, int requestedPage,
            int requestedSize, UUID tenantId) {
        requirePlatformCommercialRead(caller);
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 100);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.pte.billing.domain.Subscription> subscriptions = tenantId == null
                ? subscriptionRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                : subscriptionRepository.findByDeletedFalseAndTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        return new PagedResult<>(subscriptions.map(SubscriptionView::from).getContent(),
                new PageMeta(subscriptions.getNumber(), subscriptions.getSize(), subscriptions.getTotalElements(),
                        subscriptions.getTotalPages(), subscriptions.isFirst(), subscriptions.isLast(),
                        subscriptions.hasNext(), subscriptions.hasPrevious()));
    }

    private void requirePlatformCommercialRead(CurrentUser caller) {
        if (PlatformOperationPolicy.can(caller, PlatformOperation.COMMERCIAL_READ)) {
            return;
        }
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, BillingConstants.SUBSCRIPTION_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, PlatformOperation.COMMERCIAL_READ.name());
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    /**
     * Returns whether the tenant has a currently usable EXAM_PACKAGE
     * subscription. Plan type is checked here so callers do not reach into the
     * billing repositories or accidentally treat capacity-only grants as exam
     * access.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveExamPackageSubscription(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        List<SubscriptionView> subscriptions = listActiveSubscriptions(tenantId);
        if (subscriptions.isEmpty()) {
            return false;
        }
        Set<UUID> planIds = subscriptions.stream()
                .map(SubscriptionView::planId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return planRepository.findByPublicIdIn(planIds).stream()
                .map(Plan::getType)
                .anyMatch(PlanType.EXAM_PACKAGE::equals);
    }
}
