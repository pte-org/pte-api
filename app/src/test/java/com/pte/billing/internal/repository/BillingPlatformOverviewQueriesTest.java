package com.pte.billing.internal.repository;

import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.LicenseCounts;
import com.pte.billing.BillingOverviewService.PendingApplicationView;
import com.pte.billing.BillingOverviewService.PlanCounts;
import com.pte.billing.BillingOverviewService.SubscriptionCounts;
import com.pte.billing.BillingOverviewService.TenantPlanBrief;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform-wide commercial reads behind {@link BillingOverviewService} (licenses, subscription counts, activation
 * sources, plan catalogue, applications and the batched current-plan lookup) on the embedded database with a fixed
 * clock: soft delete, derived license expiry, half-open ranges and threshold boundaries.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BillingPlatformOverviewQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ISSUER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private LicenseCodeRepository licenseCodes;
    @Autowired
    private TenantApplicationRepository applications;
    @Autowired
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;

    private BillingOverviewService service;
    private int counter;

    @BeforeEach
    void setUp() {
        service = new BillingOverviewService(subscriptions, plans, orders, licenseCodes, applications, CLOCK);
    }

    // ---- builders ----

    private Plan plan(String name, PlanType type, PlanStatus status, boolean deleted) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setType(type);
        plan.setPrice(new BigDecimal("100000.00"));
        plan.setCurrency("VND");
        plan.setStatus(status);
        plan.setDeleted(deleted);
        return plans.saveAndFlush(plan);
    }

    private Plan plan(String name) {
        return plan(name, PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE, false);
    }

    private LicenseCode code(Instant codeExpiresAt) {
        return licenseCodes.saveAndFlush(LicenseCode.issue("SECRET-CODE-" + (++counter) + "-"
                + UUID.randomUUID().toString().substring(0, 8), UUID.randomUUID(), ISSUER, NOW.minus(Duration.ofDays(60)),
                codeExpiresAt));
    }

    private LicenseCode redeemed(Instant redeemedAt, Instant codeExpiresAt) {
        LicenseCode code = code(codeExpiresAt);
        code.markRedeemed(UUID.randomUUID(), redeemedAt);
        return licenseCodes.saveAndFlush(code);
    }

    private LicenseCode deleted(LicenseCode code) {
        code.setDeleted(true);
        return licenseCodes.saveAndFlush(code);
    }

    private Subscription subscription(UUID tenant, UUID planPublicId, Instant startsAt, Instant expiresAt,
            SubscriptionStatus status, ActivationSource source, boolean deleted, Instant createdAt) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant);
        subscription.setPlanId(planPublicId);
        subscription.setLicenseKey("LIC-" + (++counter) + "-" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStartsAt(startsAt);
        subscription.setExpiresAt(expiresAt);
        subscription.setMaxStudentsPerSession(30);
        subscription.setStatus(status);
        subscription.setActivationSource(source);
        subscription.setDeleted(deleted);
        subscriptions.saveAndFlush(subscription);
        rewriteCreatedAt("Subscription", subscription.getId(), createdAt);
        return subscription;
    }

    private Subscription usable(UUID tenant, Plan plan, Duration untilExpiry) {
        return subscription(tenant, plan.getPublicId(), NOW.minus(Duration.ofDays(100)), NOW.plus(untilExpiry),
                SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE, false, null);
    }

    private TenantApplication application(TenantApplicationStatus status, boolean deleted, Instant createdAt) {
        TenantApplication application = new TenantApplication();
        application.setOrgName("Org " + (++counter));
        application.setOrgType("SCHOOL");
        application.setRequestedCode("code" + counter + UUID.randomUUID().toString().substring(0, 6));
        application.setContactEmail("secret-contact-" + counter + "@example.test");
        application.setContactPhone("0900000000");
        application.setTaxCode("TAX-SECRET-" + counter);
        application.setStatus(status);
        application.setDeleted(deleted);
        applications.saveAndFlush(application);
        rewriteCreatedAt("TenantApplication", application.getId(), createdAt);
        return application;
    }

    private void rewriteCreatedAt(String entity, Long id, Instant createdAt) {
        if (createdAt != null) {
            // createdAt is @CreationTimestamp and not updatable: rewrite it with a bulk update.
            em.createQuery("update " + entity + " e set e.createdAt = :c where e.id = :id")
                    .setParameter("c", createdAt).setParameter("id", id).executeUpdate();
        }
        em.clear();
    }

    // ---- license counts ----

    @Test
    void licenseCountsSplitByDerivedStateWithAnIssuedCodePastExpiryCountedAsExpired() {
        code(null); // valid, never expires
        code(NOW.plus(Duration.ofDays(90))); // valid, far future
        code(NOW.plus(Duration.ofDays(3))); // valid, expiring soon
        code(NOW.minusSeconds(1)); // ISSUED but past expiry -> derived expired
        code(NOW.minus(Duration.ofDays(5))); // ISSUED but past expiry -> derived expired
        LicenseCode stored = code(NOW.minus(Duration.ofDays(1)));
        stored.expire(); // stored EXPIRED
        licenseCodes.saveAndFlush(stored);
        redeemed(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(30))); // redeemed stays redeemed even if past expiry
        redeemed(NOW.minus(Duration.ofDays(40)), null);
        LicenseCode revoked = code(NOW.minus(Duration.ofDays(1)));
        revoked.revoke("test");
        licenseCodes.saveAndFlush(revoked);

        LicenseCounts counts = service.licenseCounts(NOW);

        assertThat(counts.issuedValid()).isEqualTo(3);
        assertThat(counts.redeemed()).isEqualTo(2);
        assertThat(counts.revoked()).isEqualTo(1);
        assertThat(counts.expired()).isEqualTo(3); // 2 derived + 1 stored
        assertThat(counts.expiringSoon()).isEqualTo(1);
    }

    @Test
    void anIssuedCodeExpiringExactlyNowIsAlreadyExpiredAndNeverExpiringSoon() {
        code(NOW);

        LicenseCounts counts = service.licenseCounts(NOW);

        assertThat(counts.expired()).isEqualTo(1);
        assertThat(counts.issuedValid()).isZero();
        assertThat(counts.expiringSoon()).isZero();
    }

    @Test
    void theExpiringSoonThresholdIsSevenDaysInclusiveOfTheLastInstant() {
        code(NOW.plus(Duration.ofDays(7))); // exactly 7 days: expiring soon
        code(NOW.plus(Duration.ofDays(7)).plusSeconds(1)); // just past the threshold: valid but not soon
        code(NOW.plusSeconds(1)); // about to expire: soon
        code(null); // never expires: not soon

        LicenseCounts counts = service.licenseCounts(NOW);

        assertThat(counts.expiringSoon()).isEqualTo(2);
        assertThat(counts.issuedValid()).isEqualTo(4);
        assertThat(BillingOverviewService.LICENSE_EXPIRING_SOON_DAYS).isEqualTo(7);
    }

    @Test
    void deletedLicenseCodesAreNeverCounted() {
        deleted(code(null));
        deleted(code(NOW.minusSeconds(10)));
        deleted(redeemed(NOW.minusSeconds(10), null));
        code(null);

        LicenseCounts counts = service.licenseCounts(NOW);

        assertThat(counts).isEqualTo(new LicenseCounts(1, 0, 0, 0, 0));
    }

    @Test
    void noLicenseCodesMeansAllZeros() {
        assertThat(service.licenseCounts(NOW)).isEqualTo(new LicenseCounts(0, 0, 0, 0, 0));
    }

    @Test
    void licenseCountsExposeNumbersOnlyNeverACodeValue() {
        code(null);

        assertThat(Arrays.stream(LicenseCounts.class.getRecordComponents()).map(RecordComponent::getType))
                .containsOnly(long.class);
        assertThat(service.licenseCounts(NOW).toString()).doesNotContain("SECRET-CODE");
    }

    @Test
    void licenseCountsTakeAFixedNumberOfStatementsAndLoadNoEntity() {
        for (int i = 0; i < 20; i++) {
            code(i % 2 == 0 ? null : NOW.plus(Duration.ofDays(i)));
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        service.licenseCounts(NOW);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void redeemedInWindowIsHalfOpenIncludesLaterRevokedCodesAndSkipsDeletedOnes() {
        Instant from = NOW.minus(Duration.ofDays(30));
        redeemed(from, null); // lower bound included
        redeemed(NOW.minusSeconds(1), null);
        redeemed(NOW, null); // upper bound excluded
        redeemed(from.minusSeconds(1), null); // before
        LicenseCode later = redeemed(NOW.minus(Duration.ofDays(3)), null);
        later.revoke("fraud"); // still redeemed in the window historically
        licenseCodes.saveAndFlush(later);
        deleted(redeemed(NOW.minus(Duration.ofDays(2)), null));
        code(null); // issued, never redeemed

        assertThat(service.countLicensesRedeemedBetween(from, NOW)).isEqualTo(3);
    }

    @Test
    void adjacentRedeemedRangesPartition() {
        Instant start = NOW.minus(Duration.ofDays(20));
        Instant middle = NOW.minus(Duration.ofDays(10));
        redeemed(start, null);
        redeemed(middle, null);
        redeemed(NOW.minusSeconds(1), null);

        long first = service.countLicensesRedeemedBetween(start, middle);
        long second = service.countLicensesRedeemedBetween(middle, NOW);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(first + second).isEqualTo(service.countLicensesRedeemedBetween(start, NOW));
    }

    // ---- subscription counts ----

    @Test
    void subscriptionCountsCoverEveryTenantAndOnlyUsableSubscriptions() {
        Plan plan = plan("Pro");
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        usable(tenantA, plan, Duration.ofDays(90));
        usable(tenantA, plan, Duration.ofDays(10));
        usable(tenantB, plan, Duration.ofDays(200));
        subscription(tenantB, plan.getPublicId(), NOW.minusSeconds(1000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.EXPIRED, ActivationSource.PAYMENT, false, null);
        subscription(tenantB, plan.getPublicId(), NOW.minusSeconds(1000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.CANCELLED, ActivationSource.PAYMENT, false, null);
        subscription(tenantB, plan.getPublicId(), NOW.minusSeconds(1000), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.ACTIVE, ActivationSource.PAYMENT, true, null); // deleted
        subscription(tenantB, plan.getPublicId(), NOW.plusSeconds(1), NOW.plus(Duration.ofDays(5)),
                SubscriptionStatus.ACTIVE, ActivationSource.PAYMENT, false, null); // not started
        usable(tenantB, plan, Duration.ofDays(-2)); // marked active but already expired

        assertThat(service.subscriptionCounts(NOW)).isEqualTo(new SubscriptionCounts(3, 1));
    }

    @Test
    void theExpiringSoonThresholdIsThirtyDaysInclusive() {
        Plan plan = plan("Pro");
        UUID tenant = UUID.randomUUID();
        usable(tenant, plan, Duration.ofDays(30)); // exactly on the threshold: soon
        usable(tenant, plan, Duration.ofDays(30).plusSeconds(1)); // not soon
        usable(tenant, plan, Duration.ofDays(29)); // soon
        usable(tenant, plan, Duration.ofSeconds(1)); // soon
        usable(tenant, plan, Duration.ZERO); // expires exactly now: not usable at all
        subscription(tenant, plan.getPublicId(), NOW, NOW.plus(Duration.ofDays(10)), SubscriptionStatus.ACTIVE,
                ActivationSource.PAYMENT, false, null); // starts exactly now: usable and soon

        SubscriptionCounts counts = service.subscriptionCounts(NOW);

        assertThat(counts.active()).isEqualTo(5);
        assertThat(counts.expiringSoon()).isEqualTo(4);
        assertThat(BillingOverviewService.EXPIRING_SOON_DAYS).isEqualTo(30);
    }

    @Test
    void noSubscriptionsMeansZeroCounts() {
        assertThat(service.subscriptionCounts(NOW)).isEqualTo(new SubscriptionCounts(0, 0));
    }

    // ---- activation sources ----

    @Test
    void activatedBySourceIsHalfOpenOnCreationTimeAndEverySourceIsPresent() {
        Plan plan = plan("Pro");
        UUID tenant = UUID.randomUUID();
        Instant from = NOW.minus(Duration.ofDays(30));
        Instant start = NOW.minus(Duration.ofDays(50));
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.ACTIVE,
                ActivationSource.PAYMENT, false, from); // lower bound included
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.ACTIVE,
                ActivationSource.PAYMENT, false, NOW.minusSeconds(1));
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.ACTIVE,
                ActivationSource.PAYMENT, false, NOW); // upper bound excluded
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.EXPIRED,
                ActivationSource.LICENSE_CODE, false, NOW.minus(Duration.ofDays(2))); // status does not matter
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.ACTIVE,
                ActivationSource.LICENSE_CODE, true, NOW.minus(Duration.ofDays(2))); // deleted
        subscription(tenant, plan.getPublicId(), start, NOW.plusSeconds(1000), SubscriptionStatus.ACTIVE,
                ActivationSource.LICENSE_CODE, false, from.minusSeconds(1)); // before

        Map<String, Long> bySource = service.subscriptionsActivatedBySource(from, NOW);

        assertThat(bySource).containsOnlyKeys("PAYMENT", "LICENSE_CODE");
        assertThat(bySource).containsEntry("PAYMENT", 2L).containsEntry("LICENSE_CODE", 1L);
    }

    @Test
    void activatedBySourceListsEverySourceWithZeroWhenNothingMatches() {
        Map<String, Long> bySource = service.subscriptionsActivatedBySource(NOW.minus(Duration.ofDays(30)), NOW);

        assertThat(bySource).containsEntry("PAYMENT", 0L).containsEntry("LICENSE_CODE", 0L)
                .hasSize(ActivationSource.values().length);
    }

    // ---- plan counts ----

    @Test
    void planCountsSplitByStatusAndByTypeAndSkipDeletedPlans() {
        plan("a1", PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE, false);
        plan("a2", PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE, false);
        plan("a3", PlanType.STUDENT_CAPACITY, PlanStatus.ACTIVE, false);
        plan("d1", PlanType.EXAM_PACKAGE, PlanStatus.DRAFT, false);
        plan("d2", PlanType.STUDENT_CAPACITY, PlanStatus.DRAFT, false);
        plan("r1", PlanType.STUDENT_CAPACITY, PlanStatus.ARCHIVED, false);
        plan("gone", PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE, true);

        PlanCounts counts = service.planCounts();

        assertThat(counts.active()).isEqualTo(3);
        assertThat(counts.draft()).isEqualTo(2);
        assertThat(counts.archived()).isEqualTo(1);
        assertThat(counts.byType()).containsEntry("EXAM_PACKAGE", 3L).containsEntry("STUDENT_CAPACITY", 3L);
    }

    @Test
    void planCountsListEveryTypeEvenWhenThereAreNoPlans() {
        PlanCounts counts = service.planCounts();

        assertThat(counts.active()).isZero();
        assertThat(counts.draft()).isZero();
        assertThat(counts.archived()).isZero();
        assertThat(counts.byType()).containsOnlyKeys("EXAM_PACKAGE", "STUDENT_CAPACITY");
        assertThat(counts.byType().values()).containsOnly(0L);
    }

    // ---- applications ----

    @Test
    void pendingCountIgnoresReviewedAndDeletedApplications() {
        application(TenantApplicationStatus.PENDING, false, null);
        application(TenantApplicationStatus.PENDING, false, null);
        application(TenantApplicationStatus.APPROVED, false, null);
        application(TenantApplicationStatus.REJECTED, false, null);
        application(TenantApplicationStatus.PENDING, true, null);

        assertThat(service.pendingApplicationCount()).isEqualTo(2);
    }

    @Test
    void receivedInWindowIsHalfOpenCountsEveryStatusAndSkipsDeleted() {
        Instant from = NOW.minus(Duration.ofDays(30));
        application(TenantApplicationStatus.PENDING, false, from); // lower bound included
        application(TenantApplicationStatus.APPROVED, false, NOW.minusSeconds(1));
        application(TenantApplicationStatus.REJECTED, false, NOW.minus(Duration.ofDays(3)));
        application(TenantApplicationStatus.PENDING, false, NOW); // upper bound excluded
        application(TenantApplicationStatus.PENDING, false, from.minusSeconds(1)); // before
        application(TenantApplicationStatus.PENDING, true, NOW.minus(Duration.ofDays(3))); // deleted

        assertThat(service.countApplicationsReceivedBetween(from, NOW)).isEqualTo(3);
    }

    @Test
    void oldestPendingAreOldestFirstLimitedAndOnlyPending() {
        TenantApplication newest = application(TenantApplicationStatus.PENDING, false, NOW.minus(Duration.ofDays(1)));
        TenantApplication oldest = application(TenantApplicationStatus.PENDING, false, NOW.minus(Duration.ofDays(40)));
        TenantApplication middle = application(TenantApplicationStatus.PENDING, false, NOW.minus(Duration.ofDays(10)));
        application(TenantApplicationStatus.APPROVED, false, NOW.minus(Duration.ofDays(90)));
        application(TenantApplicationStatus.REJECTED, false, NOW.minus(Duration.ofDays(80)));
        application(TenantApplicationStatus.PENDING, true, NOW.minus(Duration.ofDays(70)));

        List<PendingApplicationView> two = service.oldestPendingApplications(2);
        List<PendingApplicationView> all = service.oldestPendingApplications(10);

        assertThat(two).extracting(PendingApplicationView::publicId).containsExactly(oldest.getPublicId(),
                middle.getPublicId());
        assertThat(all).extracting(PendingApplicationView::publicId).containsExactly(oldest.getPublicId(),
                middle.getPublicId(), newest.getPublicId());
    }

    @Test
    void oldestPendingViewCarriesOnlyNonSensitiveFieldsAndTheReceivedTime() {
        TenantApplication saved = application(TenantApplicationStatus.PENDING, false, NOW.minus(Duration.ofDays(2)));

        PendingApplicationView view = service.oldestPendingApplications(1).get(0);

        assertThat(view.publicId()).isEqualTo(saved.getPublicId());
        assertThat(view.orgName()).isEqualTo(saved.getOrgName());
        assertThat(view.orgType()).isEqualTo("SCHOOL");
        assertThat(view.requestedCode()).isEqualTo(saved.getRequestedCode());
        assertThat(view.receivedAt()).isEqualTo(NOW.minus(Duration.ofDays(2)));
        assertThat(view.toString()).doesNotContain("secret-contact").doesNotContain("TAX-SECRET")
                .doesNotContain("0900000000");
        assertThat(Arrays.stream(PendingApplicationView.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("publicId", "orgName", "orgType", "requestedCode", "receivedAt");
    }

    @Test
    void applicationsCreatedAtTheSameInstantAreOrderedByInsertionOrderForStability() {
        Instant same = NOW.minus(Duration.ofDays(3));
        TenantApplication first = application(TenantApplicationStatus.PENDING, false, same);
        TenantApplication second = application(TenantApplicationStatus.PENDING, false, same);

        assertThat(service.oldestPendingApplications(5)).extracting(PendingApplicationView::publicId)
                .containsExactly(first.getPublicId(), second.getPublicId());
    }

    @Test
    void aNonPositiveLimitReturnsNothingWithoutQuerying() {
        application(TenantApplicationStatus.PENDING, false, null);
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.oldestPendingApplications(0)).isEmpty();
        assertThat(service.oldestPendingApplications(-1)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    // ---- currentPlanByTenant: batched ----

    @Test
    void eachTenantGetsItsSoonestExpiringUsableSubscriptionsPlanAndExpiry() {
        Plan pro = plan("Pro Pack");
        Plan lite = plan("Lite Pack");
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        usable(tenantA, lite, Duration.ofDays(200));
        usable(tenantA, pro, Duration.ofDays(20)); // soonest for A
        usable(tenantB, lite, Duration.ofDays(60));

        Map<UUID, TenantPlanBrief> briefs = service.currentPlanByTenant(List.of(tenantA, tenantB), NOW);

        assertThat(briefs).containsOnlyKeys(tenantA, tenantB);
        assertThat(briefs.get(tenantA)).isEqualTo(new TenantPlanBrief("Pro Pack", NOW.plus(Duration.ofDays(20))));
        assertThat(briefs.get(tenantB)).isEqualTo(new TenantPlanBrief("Lite Pack", NOW.plus(Duration.ofDays(60))));
    }

    @Test
    void tenantsWithoutAUsableSubscriptionAreAbsentAndExpiredOrFutureOrDeletedOnesDoNotCount() {
        Plan pro = plan("Pro Pack");
        UUID withNothing = UUID.randomUUID();
        UUID expiredOnly = UUID.randomUUID();
        UUID futureOnly = UUID.randomUUID();
        UUID deletedOnly = UUID.randomUUID();
        UUID cancelledOnly = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        usable(expiredOnly, pro, Duration.ofDays(-1));
        subscription(futureOnly, pro.getPublicId(), NOW.plusSeconds(1), NOW.plus(Duration.ofDays(9)),
                SubscriptionStatus.ACTIVE, ActivationSource.PAYMENT, false, null);
        subscription(deletedOnly, pro.getPublicId(), NOW.minusSeconds(10), NOW.plus(Duration.ofDays(9)),
                SubscriptionStatus.ACTIVE, ActivationSource.PAYMENT, true, null);
        subscription(cancelledOnly, pro.getPublicId(), NOW.minusSeconds(10), NOW.plus(Duration.ofDays(9)),
                SubscriptionStatus.CANCELLED, ActivationSource.PAYMENT, false, null);
        usable(good, pro, Duration.ofDays(9));

        Map<UUID, TenantPlanBrief> briefs = service.currentPlanByTenant(
                List.of(withNothing, expiredOnly, futureOnly, deletedOnly, cancelledOnly, good), NOW);

        assertThat(briefs).containsOnlyKeys(good);
    }

    @Test
    void subscriptionsOfTenantsThatWereNotAskedForNeverAppear() {
        Plan pro = plan("Pro Pack");
        UUID asked = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        usable(asked, pro, Duration.ofDays(5));
        usable(other, pro, Duration.ofDays(1));

        assertThat(service.currentPlanByTenant(List.of(asked), NOW)).containsOnlyKeys(asked);
    }

    @Test
    void aSubscriptionWhosePlanCannotBeResolvedStillReportsItsExpiryWithANullPlanName() {
        UUID tenant = UUID.randomUUID();
        subscription(tenant, UUID.randomUUID(), NOW.minusSeconds(100), NOW.plus(Duration.ofDays(4)),
                SubscriptionStatus.ACTIVE, ActivationSource.PAYMENT, false, null);

        TenantPlanBrief brief = service.currentPlanByTenant(List.of(tenant), NOW).get(tenant);

        assertThat(brief.planName()).isNull();
        assertThat(brief.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(4)));
    }

    @Test
    void anEmptyOrNullTenantListIsAnEmptyMapWithoutAnyQuery() {
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.currentPlanByTenant(List.of(), NOW)).isEmpty();
        assertThat(service.currentPlanByTenant(null, NOW)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void currentPlansForManyTenantsAreResolvedInTwoStatementsNotOnePerTenant() {
        Plan planA = plan("Plan A");
        Plan planB = plan("Plan B");
        Plan planC = plan("Plan C");
        List<UUID> tenants = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID tenant = UUID.randomUUID();
            tenants.add(tenant);
            usable(tenant, i % 3 == 0 ? planA : i % 3 == 1 ? planB : planC, Duration.ofDays(i + 1));
            usable(tenant, planC, Duration.ofDays(100 + i)); // a later second subscription
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Map<UUID, TenantPlanBrief> briefs = service.currentPlanByTenant(tenants, NOW);

        assertThat(briefs).hasSize(6);
        assertThat(briefs.get(tenants.get(0)).planName()).isEqualTo("Plan A");
        assertThat(briefs.get(tenants.get(1)).planName()).isEqualTo("Plan B");
        assertThat(briefs.get(tenants.get(2)).planName()).isEqualTo("Plan C");
        // one statement for every tenant's subscriptions + one for all the plan names
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
