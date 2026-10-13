package com.pte.billing;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only commercial summary for dashboards. Tenant-scoped and soft-delete
 * filtered. Plan names are resolved in one batch query. Order data is exposed as a
 * count only: no payment link, provider reference or amount ever leaves billing
 * through this facade.
 */
@Service
public class BillingOverviewService {

    /** A subscription ending within this many days is flagged as expiring soon. */
    public static final int EXPIRING_SOON_DAYS = 30;
    /** Upper bound on listed subscriptions; the nearest expiry is always the first row. */
    public static final int MAX_CURRENT_SUBSCRIPTIONS = 10;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public BillingOverviewService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            OrderRepository orderRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
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

    private static CurrentSubscriptionView toView(Subscription subscription, Plan plan) {
        return new CurrentSubscriptionView(subscription.getPublicId(), plan == null ? null : plan.getName(),
                plan == null ? null : plan.getType().name(), subscription.getStartsAt(), subscription.getExpiresAt(),
                subscription.getStatus().name());
    }
}
