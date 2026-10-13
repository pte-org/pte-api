package com.pte.billing;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.repository.TenantApplicationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only commercial summary for dashboards, soft-delete filtered. The tenant
 * methods are tenant-scoped; the platform methods (licenses, subscription counts,
 * plans, applications) read every tenant and are for platform dashboards only. Plan
 * names are resolved in one batch query. Order data is exposed as a count only: no
 * payment link, provider reference or amount ever leaves billing through this facade,
 * and license data is exposed as counts only (never a code value).
 */
@Service
public class BillingOverviewService {

    /** A subscription ending within this many days is flagged as expiring soon. */
    public static final int EXPIRING_SOON_DAYS = 30;
    /** Upper bound on listed subscriptions; the nearest expiry is always the first row. */
    public static final int MAX_CURRENT_SUBSCRIPTIONS = 10;

    /** An issued license code expiring within this many days is flagged as expiring soon (platform view). */
    public static final int LICENSE_EXPIRING_SOON_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final OrderRepository orderRepository;
    private final LicenseCodeRepository licenseCodeRepository;
    private final TenantApplicationRepository applicationRepository;
    private final Clock clock;

    public BillingOverviewService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            OrderRepository orderRepository, LicenseCodeRepository licenseCodeRepository,
            TenantApplicationRepository applicationRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.licenseCodeRepository = licenseCodeRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    /**
     * Platform license-code counts by derived state: an ISSUED code past its expiry counts as
     * {@code expired} (same rule as the admin license list). Counts only: no code value or secret.
     */
    public record LicenseCounts(long issuedValid, long redeemed, long revoked, long expired, long expiringSoon) {
    }

    /** Platform subscription counts at one instant: usable now, and of those the ones ending soon. */
    public record SubscriptionCounts(long active, long expiringSoon) {
    }

    /** Platform plan catalogue counts; {@code byType} holds every plan type (0 when none). */
    public record PlanCounts(long active, long draft, long archived, Map<String, Long> byType) {
    }

    /** Pending application reduced to non-sensitive fields (no contact, tax code or review data). */
    public record PendingApplicationView(UUID publicId, String orgName, String orgType, String requestedCode,
            Instant receivedAt) {
    }

    /** The plan name and expiry of a tenant's soonest-expiring usable subscription. */
    public record TenantPlanBrief(String planName, Instant expiresAt) {
    }

    /**
     * Usable subscriptions soonest-expiry first. {@code nearestExpiryAt} and {@code daysToExpiry}
     * (whole days, rounded down, computed from the injected clock) are null when there is none.
     */
    public record SubscriptionSummary(List<CurrentSubscriptionView> current, Instant nearestExpiryAt,
            Long daysToExpiry, boolean expiringSoon) {
    }

    @Transactional(readOnly = true)
    public SubscriptionSummary subscriptionSummary(UUID tenantId) {
        Instant now = clock.instant();
        List<Subscription> subscriptions = subscriptionRepository.findUsableForOverview(tenantId,
                SubscriptionStatus.ACTIVE, now, PageRequest.of(0, MAX_CURRENT_SUBSCRIPTIONS));
        if (subscriptions.isEmpty()) {
            return new SubscriptionSummary(List.of(), null, null, false);
        }
        Map<UUID, Plan> plans = planRepository
                .findByPublicIdIn(subscriptions.stream().map(Subscription::getPlanId).distinct().toList()).stream()
                .collect(Collectors.toMap(Plan::getPublicId, Function.identity()));
        List<CurrentSubscriptionView> current = subscriptions.stream().map(s -> toView(s, plans.get(s.getPlanId())))
                .toList();
        Instant nearest = subscriptions.get(0).getExpiresAt();
        boolean expiringSoon = !nearest.isAfter(now.plus(Duration.ofDays(EXPIRING_SOON_DAYS)));
        return new SubscriptionSummary(current, nearest, ChronoUnit.DAYS.between(now, nearest), expiringSoon);
    }

    /** Orders of the tenant still waiting for payment. */
    @Transactional(readOnly = true)
    public long pendingOrderCount(UUID tenantId) {
        return orderRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, OrderStatus.PENDING);
    }

    /** Platform license counts at {@code now} (3 counts + 1 grouped query; no code values are read). */
    @Transactional(readOnly = true)
    public LicenseCounts licenseCounts(Instant now) {
        Map<LicenseCodeStatus, Long> stored = new EnumMap<>(LicenseCodeStatus.class);
        for (Object[] row : licenseCodeRepository.countGroupedByStatus()) {
            stored.put((LicenseCodeStatus) row[0], ((Number) row[1]).longValue());
        }
        long issuedPastExpiry = licenseCodeRepository.countIssuedPastExpiry(now);
        long issuedValid = Math.max(0L, stored.getOrDefault(LicenseCodeStatus.ISSUED, 0L) - issuedPastExpiry);
        long expiringSoon = licenseCodeRepository.countIssuedExpiringBetween(now,
                now.plus(Duration.ofDays(LICENSE_EXPIRING_SOON_DAYS)));
        return new LicenseCounts(issuedValid, stored.getOrDefault(LicenseCodeStatus.REDEEMED, 0L),
                stored.getOrDefault(LicenseCodeStatus.REVOKED, 0L),
                stored.getOrDefault(LicenseCodeStatus.EXPIRED, 0L) + issuedPastExpiry, expiringSoon);
    }

    /** License codes redeemed in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countLicensesRedeemedBetween(Instant from, Instant to) {
        return licenseCodeRepository.countRedeemedBetween(from, to);
    }

    /** Platform subscriptions usable at {@code now} and those ending within {@link #EXPIRING_SOON_DAYS}. */
    @Transactional(readOnly = true)
    public SubscriptionCounts subscriptionCounts(Instant now) {
        return new SubscriptionCounts(subscriptionRepository.countUsable(SubscriptionStatus.ACTIVE, now),
                subscriptionRepository.countUsableExpiringBy(SubscriptionStatus.ACTIVE, now,
                        now.plus(Duration.ofDays(EXPIRING_SOON_DAYS))));
    }

    /** Subscriptions created (activated) in {@code [from, to)} by activation source; every source is present. */
    @Transactional(readOnly = true)
    public Map<String, Long> subscriptionsActivatedBySource(Instant from, Instant to) {
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (ActivationSource source : ActivationSource.values()) {
            bySource.put(source.name(), 0L);
        }
        for (Object[] row : subscriptionRepository.countActivatedBySource(from, to)) {
            bySource.put(((ActivationSource) row[0]).name(), ((Number) row[1]).longValue());
        }
        return Collections.unmodifiableMap(bySource);
    }

    /** Plan catalogue counts by status and type (plans are few; two grouped queries). */
    @Transactional(readOnly = true)
    public PlanCounts planCounts() {
        Map<PlanStatus, Long> byStatus = new EnumMap<>(PlanStatus.class);
        for (Object[] row : planRepository.countGroupedByStatus()) {
            byStatus.put((PlanStatus) row[0], ((Number) row[1]).longValue());
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (PlanType type : PlanType.values()) {
            byType.put(type.name(), 0L);
        }
        for (Object[] row : planRepository.countGroupedByType()) {
            byType.put(((PlanType) row[0]).name(), ((Number) row[1]).longValue());
        }
        return new PlanCounts(byStatus.getOrDefault(PlanStatus.ACTIVE, 0L), byStatus.getOrDefault(PlanStatus.DRAFT, 0L),
                byStatus.getOrDefault(PlanStatus.ARCHIVED, 0L), Collections.unmodifiableMap(byType));
    }

    /** Applications waiting for review. */
    @Transactional(readOnly = true)
    public long pendingApplicationCount() {
        return applicationRepository.countByStatusAndDeletedFalse(TenantApplicationStatus.PENDING);
    }

    /** Applications received (created) in {@code [from, to)}, whatever their review state is now. */
    @Transactional(readOnly = true)
    public long countApplicationsReceivedBetween(Instant from, Instant to) {
        return applicationRepository.countReceivedBetween(from, to);
    }

    /** The {@code limit} longest-waiting pending applications, oldest first. */
    @Transactional(readOnly = true)
    public List<PendingApplicationView> oldestPendingApplications(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return applicationRepository.findOldestByStatus(TenantApplicationStatus.PENDING, PageRequest.of(0, limit))
                .stream()
                .map(a -> new PendingApplicationView(a.getPublicId(), a.getOrgName(), a.getOrgType(),
                        a.getRequestedCode(), a.getCreatedAt()))
                .toList();
    }

    /**
     * Plan name and expiry of each given tenant's soonest-expiring usable subscription, in two
     * batched queries (subscriptions for all tenants, then plans by id); tenants with none are absent.
     */
    @Transactional(readOnly = true)
    public Map<UUID, TenantPlanBrief> currentPlanByTenant(Collection<UUID> tenantIds, Instant now) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        List<Subscription> usable = subscriptionRepository.findUsableForTenants(tenantIds,
                SubscriptionStatus.ACTIVE, now);
        Map<UUID, Subscription> soonest = new LinkedHashMap<>();
        usable.forEach(s -> soonest.putIfAbsent(s.getTenantId(), s));
        Map<UUID, Plan> plans = planRepository
                .findByPublicIdIn(soonest.values().stream().map(Subscription::getPlanId).distinct().toList()).stream()
                .collect(Collectors.toMap(Plan::getPublicId, Function.identity()));
        Map<UUID, TenantPlanBrief> result = new LinkedHashMap<>();
        soonest.forEach((tenantId, s) -> {
            Plan plan = plans.get(s.getPlanId());
            result.put(tenantId, new TenantPlanBrief(plan == null ? null : plan.getName(), s.getExpiresAt()));
        });
        return Collections.unmodifiableMap(result);
    }

    private static CurrentSubscriptionView toView(Subscription subscription, Plan plan) {
        return new CurrentSubscriptionView(subscription.getPublicId(), plan == null ? null : plan.getName(),
                plan == null ? null : plan.getType().name(), subscription.getStartsAt(), subscription.getExpiresAt(),
                subscription.getStatus().name());
    }
}
