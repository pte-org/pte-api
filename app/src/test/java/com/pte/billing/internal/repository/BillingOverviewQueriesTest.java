package com.pte.billing.internal.repository;

import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.SubscriptionSummary;
import com.pte.billing.CurrentSubscriptionView;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subscription summary and pending-order count behind {@link BillingOverviewService} on the embedded database,
 * with a fixed clock: expiry boundaries, soft delete, tenant isolation, plan-name batching.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BillingOverviewQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private BillingOverviewService service;
    private int licenseCounter;

    @BeforeEach
    void setUp() {
        service = new BillingOverviewService(subscriptions, plans, orders, CLOCK);
    }

    private Plan plan(String name, PlanType type) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setType(type);
        plan.setPrice(new BigDecimal("100000.00"));
        plan.setCurrency("VND");
        plan.setStatus(PlanStatus.ACTIVE);
        return plans.saveAndFlush(plan);
    }

    private Subscription subscription(UUID tenant, UUID planPublicId, Instant startsAt, Instant expiresAt,
                                      SubscriptionStatus status, boolean deleted) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant);
        subscription.setPlanId(planPublicId);
        subscription.setLicenseKey("LIC-" + (++licenseCounter) + "-" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStartsAt(startsAt);
        subscription.setExpiresAt(expiresAt);
        subscription.setMaxStudentsPerSession(30);
        subscription.setStatus(status);
        subscription.setActivationSource(ActivationSource.LICENSE_CODE);
        subscription.setDeleted(deleted);
        return subscriptions.saveAndFlush(subscription);
    }

    private Subscription expiringIn(UUID tenant, Plan plan, Duration fromNow) {
        return subscription(tenant, plan.getPublicId(), NOW.minus(Duration.ofDays(100)), NOW.plus(fromNow),
                SubscriptionStatus.ACTIVE, false);
    }

    private Order order(UUID tenant, OrderStatus status, boolean deleted, String paymentLink) {
        Order order = Order.pending(tenant, UUID.randomUUID(), System.nanoTime() + (++licenseCounter),
                new BigDecimal("500000.00"), "VND");
        if (paymentLink != null) {
            order.attachPaymentLink(paymentLink);
        }
        if (status == OrderStatus.PAID) {
            order.markPaid(NOW);
        } else if (status == OrderStatus.CANCELLED) {
            order.cancel();
        } else if (status == OrderStatus.EXPIRED) {
            order.expire();
        }
        order.setDeleted(deleted);
        return orders.saveAndFlush(order);
    }

    // ---- no subscription ----

    @Test
    void aTenantWithoutAnySubscriptionGetsNullExpiryAndNotExpiringSoon() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        expiringIn(UUID.randomUUID(), plan, Duration.ofDays(5)); // someone else's

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).isEmpty();
        assertThat(summary.nearestExpiryAt()).isNull();
        assertThat(summary.daysToExpiry()).isNull();
        assertThat(summary.expiringSoon()).isFalse();
    }

    // ---- expiry boundaries (fixed clock) ----

    @Test
    void expiringExactlyThirtyDaysFromNowIsFlaggedExpiringSoonWithThirtyDays() {
        UUID tenant = UUID.randomUUID();
        expiringIn(tenant, plan("Pro", PlanType.EXAM_PACKAGE), Duration.ofDays(30));

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.expiringSoon()).isTrue();
        assertThat(summary.daysToExpiry()).isEqualTo(30);
        assertThat(summary.nearestExpiryAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void twentyNineDaysIsExpiringSoonAndThirtyOneDaysIsNot() {
        UUID soon = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        expiringIn(soon, plan, Duration.ofDays(29));
        expiringIn(later, plan, Duration.ofDays(31));

        SubscriptionSummary soonSummary = service.subscriptionSummary(soon);
        SubscriptionSummary laterSummary = service.subscriptionSummary(later);

        assertThat(soonSummary.expiringSoon()).isTrue();
        assertThat(soonSummary.daysToExpiry()).isEqualTo(29);
        assertThat(laterSummary.expiringSoon()).isFalse();
        assertThat(laterSummary.daysToExpiry()).isEqualTo(31);
    }

    @Test
    void oneSecondOverThirtyDaysIsNotExpiringSoonAndDaysRoundDown() {
        UUID tenant = UUID.randomUUID();
        expiringIn(tenant, plan("Pro", PlanType.EXAM_PACKAGE), Duration.ofDays(30).plusSeconds(1));

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.expiringSoon()).isFalse();
        assertThat(summary.daysToExpiry()).isEqualTo(30);
    }

    @Test
    void lessThanADayLeftIsZeroDaysAndExpiringSoon() {
        UUID tenant = UUID.randomUUID();
        expiringIn(tenant, plan("Pro", PlanType.EXAM_PACKAGE), Duration.ofHours(23));

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.daysToExpiry()).isZero();
        assertThat(summary.expiringSoon()).isTrue();
    }

    @Test
    void aSubscriptionExpiringExactlyNowIsAlreadyExpiredAndNotListed() {
        UUID tenant = UUID.randomUUID();
        expiringIn(tenant, plan("Pro", PlanType.EXAM_PACKAGE), Duration.ZERO);

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).isEmpty();
        assertThat(summary.nearestExpiryAt()).isNull();
        assertThat(summary.daysToExpiry()).isNull();
        assertThat(summary.expiringSoon()).isFalse();
    }

    @Test
    void anAlreadyExpiredSubscriptionIsIgnoredEvenWhenStillMarkedActive() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        expiringIn(tenant, plan, Duration.ofDays(-3));
        expiringIn(tenant, plan, Duration.ofSeconds(1));

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).hasSize(1);
        assertThat(summary.nearestExpiryAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(summary.daysToExpiry()).isZero();
    }

    // ---- which rows count ----

    @Test
    void onlyActiveStartedUndeletedSubscriptionsOfTheTenantAreListed() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        Subscription usable = expiringIn(tenant, plan, Duration.ofDays(90));
        subscription(tenant, plan.getPublicId(), NOW.minusSeconds(10000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.EXPIRED, false);
        subscription(tenant, plan.getPublicId(), NOW.minusSeconds(10000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.CANCELLED, false);
        subscription(tenant, plan.getPublicId(), NOW.minusSeconds(10000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.ACTIVE, true); // soft-deleted
        subscription(tenant, plan.getPublicId(), NOW.plusSeconds(1), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.ACTIVE, false); // not started yet
        expiringIn(UUID.randomUUID(), plan, Duration.ofDays(2)); // another tenant

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).extracting(CurrentSubscriptionView::publicId)
                .containsExactly(usable.getPublicId());
        assertThat(summary.daysToExpiry()).isEqualTo(90);
        assertThat(summary.expiringSoon()).isFalse();
    }

    @Test
    void aSubscriptionStartingExactlyNowIsAlreadyUsable() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        Subscription starting = subscription(tenant, plan.getPublicId(), NOW, NOW.plus(Duration.ofDays(60)),
                SubscriptionStatus.ACTIVE, false);

        assertThat(service.subscriptionSummary(tenant).current()).extracting(CurrentSubscriptionView::publicId)
                .containsExactly(starting.getPublicId());
    }

    @Test
    void listIsSoonestExpiryFirstAndNearestExpiryDrivesTheSummary() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        Subscription far = expiringIn(tenant, plan, Duration.ofDays(200));
        Subscription near = expiringIn(tenant, plan, Duration.ofDays(10));
        Subscription mid = expiringIn(tenant, plan, Duration.ofDays(60));

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).extracting(CurrentSubscriptionView::publicId)
                .containsExactly(near.getPublicId(), mid.getPublicId(), far.getPublicId());
        assertThat(summary.nearestExpiryAt()).isEqualTo(NOW.plus(Duration.ofDays(10)));
        assertThat(summary.daysToExpiry()).isEqualTo(10);
        assertThat(summary.expiringSoon()).isTrue();
    }

    @Test
    void listIsCappedAtTheMaximumKeepingTheNearestExpiries() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Pro", PlanType.EXAM_PACKAGE);
        for (int i = 1; i <= BillingOverviewService.MAX_CURRENT_SUBSCRIPTIONS + 5; i++) {
            expiringIn(tenant, plan, Duration.ofDays(i));
        }

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).hasSize(BillingOverviewService.MAX_CURRENT_SUBSCRIPTIONS);
        assertThat(summary.current().get(0).expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(summary.current().get(BillingOverviewService.MAX_CURRENT_SUBSCRIPTIONS - 1).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(BillingOverviewService.MAX_CURRENT_SUBSCRIPTIONS)));
    }

    @Test
    void tenantsNeverSeeEachOthersSubscriptionsOrPlanNames() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        expiringIn(tenantA, plan("Plan of A", PlanType.EXAM_PACKAGE), Duration.ofDays(40));
        expiringIn(tenantB, plan("Plan of B", PlanType.EXAM_PACKAGE), Duration.ofDays(5));

        SubscriptionSummary a = service.subscriptionSummary(tenantA);
        SubscriptionSummary b = service.subscriptionSummary(tenantB);

        assertThat(a.current()).extracting(CurrentSubscriptionView::planName).containsExactly("Plan of A");
        assertThat(a.expiringSoon()).isFalse();
        assertThat(b.current()).extracting(CurrentSubscriptionView::planName).containsExactly("Plan of B");
        assertThat(b.expiringSoon()).isTrue();
    }

    // ---- view mapping ----

    @Test
    void viewCarriesPlanNameTypeDatesAndStatusAsStrings() {
        UUID tenant = UUID.randomUUID();
        Plan plan = plan("Exam Pack 30", PlanType.EXAM_PACKAGE);
        Subscription sub = expiringIn(tenant, plan, Duration.ofDays(45));

        CurrentSubscriptionView view = service.subscriptionSummary(tenant).current().get(0);

        assertThat(view.publicId()).isEqualTo(sub.getPublicId());
        assertThat(view.planName()).isEqualTo("Exam Pack 30");
        assertThat(view.planType()).isEqualTo("EXAM_PACKAGE");
        assertThat(view.startsAt()).isEqualTo(sub.getStartsAt());
        assertThat(view.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(45)));
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void aSubscriptionWhosePlanCannotBeResolvedStillListsWithNullPlanFields() {
        UUID tenant = UUID.randomUUID();
        subscription(tenant, UUID.randomUUID(), NOW.minusSeconds(100), NOW.plus(Duration.ofDays(10)),
                SubscriptionStatus.ACTIVE, false);

        CurrentSubscriptionView view = service.subscriptionSummary(tenant).current().get(0);

        assertThat(view.planName()).isNull();
        assertThat(view.planType()).isNull();
        assertThat(view.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(10)));
    }

    // ---- plan-name batching ----

    @Test
    void planNamesAreResolvedInOneBatchQueryNotPerRow() {
        UUID tenant = UUID.randomUUID();
        Plan planA = plan("Plan A", PlanType.EXAM_PACKAGE);
        Plan planB = plan("Plan B", PlanType.EXAM_PACKAGE);
        Plan planC = plan("Plan C", PlanType.EXAM_PACKAGE);
        for (int i = 1; i <= 6; i++) {
            expiringIn(tenant, i % 3 == 0 ? planC : i % 2 == 0 ? planB : planA, Duration.ofDays(i));
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        SubscriptionSummary summary = service.subscriptionSummary(tenant);

        assertThat(summary.current()).hasSize(6);
        assertThat(summary.current()).extracting(CurrentSubscriptionView::planName)
                .containsExactly("Plan A", "Plan B", "Plan C", "Plan B", "Plan A", "Plan C");
        // One statement for the subscriptions + one for all plan names, regardless of row count.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    // ---- pending orders ----

    @Test
    void pendingOrderCountCountsOnlyPendingLiveOrdersOfTheTenant() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        order(tenant, OrderStatus.PENDING, false, "https://pay.example.test/checkout/abc");
        order(tenant, OrderStatus.PENDING, false, null);
        order(tenant, OrderStatus.PAID, false, null);
        order(tenant, OrderStatus.CANCELLED, false, null);
        order(tenant, OrderStatus.EXPIRED, false, null);
        order(tenant, OrderStatus.PENDING, true, null); // soft-deleted
        order(other, OrderStatus.PENDING, false, null);
        order(other, OrderStatus.PENDING, false, null);
        order(other, OrderStatus.PENDING, false, null);

        assertThat(service.pendingOrderCount(tenant)).isEqualTo(2);
        assertThat(service.pendingOrderCount(other)).isEqualTo(3);
        assertThat(service.pendingOrderCount(UUID.randomUUID())).isZero();
    }

    @Test
    void pendingOrderCountDoesNotLoadOrderRows() {
        UUID tenant = UUID.randomUUID();
        order(tenant, OrderStatus.PENDING, false, "https://pay.example.test/checkout/secret-token");
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.pendingOrderCount(tenant)).isEqualTo(1);

        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
