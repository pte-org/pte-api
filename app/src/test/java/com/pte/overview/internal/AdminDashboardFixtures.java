package com.pte.overview.internal;

import com.pte.attempt.AttemptOverviewService;
import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.LicenseCounts;
import com.pte.billing.BillingOverviewService.PendingApplicationView;
import com.pte.billing.BillingOverviewService.PlanCounts;
import com.pte.billing.BillingOverviewService.SubscriptionCounts;
import com.pte.billing.BillingOverviewService.TenantPlanBrief;
import com.pte.billing.BillingRevenueOverviewService;
import com.pte.billing.BillingRevenueOverviewService.CurrencyPaidTotal;
import com.pte.identity.IdentityOverviewService;
import com.pte.itembank.ItembankOverviewService;
import com.pte.itembank.ItembankOverviewService.QuestionBankCounts;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.service.AdminCommercialSections;
import com.pte.overview.internal.service.AdminDashboardService;
import com.pte.overview.internal.service.AdminQuestionBankSections;
import com.pte.overview.internal.service.AdminRevenueSections;
import com.pte.overview.internal.service.AdminTenantSections;
import com.pte.overview.internal.service.DashboardSectionRunner;
import com.pte.shared.security.CurrentUser;
import com.pte.support.SupportOverviewService;
import com.pte.support.SupportOverviewService.OpenTicketBrief;
import com.pte.support.SupportOverviewService.TicketCounts;
import com.pte.tenancy.PlatformTenantCounts;
import com.pte.tenancy.RecentTenantView;
import com.pte.tenancy.TenancyOverviewService;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.TenantSeatLimit;
import com.pte.tenancy.TenantSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixture of the Phase 3 admin dashboard tests: mocked root facades with deterministic numbers, the
 * Phase 1 fixed clock ({@link HostDashboardFixtures#NOW}, 10:00 on 2026-10-13 in Asia/Ho_Chi_Minh) and an in-memory
 * stand-in for Redis so a REAL {@link DashboardCache} can be exercised. Only root classes of other modules are used.
 */
public final class AdminDashboardFixtures {

    public static final Instant NOW = HostDashboardFixtures.NOW;
    public static final Clock CLOCK = HostDashboardFixtures.CLOCK;
    public static final Instant WINDOW_START = HostDashboardFixtures.WINDOW_START;
    public static final Instant PREVIOUS_START = HostDashboardFixtures.PREVIOUS_START;

    public static final UUID USER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    public static final UUID T1 = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    public static final UUID T2 = UUID.fromString("a2000000-0000-0000-0000-000000000002");
    public static final UUID T3 = UUID.fromString("a3000000-0000-0000-0000-000000000003");
    public static final UUID T4 = UUID.fromString("a4000000-0000-0000-0000-000000000004");
    public static final UUID T5 = UUID.fromString("a5000000-0000-0000-0000-000000000005");
    public static final UUID T6 = UUID.fromString("a6000000-0000-0000-0000-000000000006");
    /** Has students in the grouped count but no seat limit row (e.g. a soft-deleted tenant). */
    public static final UUID GHOST = UUID.fromString("ee000000-0000-0000-0000-00000000000e");

    public final TenancyService tenancy = mock(TenancyService.class);
    public final TenancyOverviewService tenancyOverview = mock(TenancyOverviewService.class);
    public final BillingRevenueOverviewService revenueFacade = mock(BillingRevenueOverviewService.class);
    public final ItembankOverviewService itembank = mock(ItembankOverviewService.class);
    public final IdentityOverviewService identity = mock(IdentityOverviewService.class);
    public final AttemptOverviewService attempts = mock(AttemptOverviewService.class);
    public final BillingOverviewService billing = mock(BillingOverviewService.class);
    public final SupportOverviewService support = mock(SupportOverviewService.class);

    // ---- callers ----

    public static CurrentUser platform(String... roles) {
        return new CurrentUser(USER, null, List.of(roles));
    }

    public static CurrentUser tenantBound(String... roles) {
        return new CurrentUser(USER, TENANT_ID, List.of(roles));
    }

    // ---- named numbers (distinct enough to be asserted one by one) ----

    public static final List<TenantSeatLimit> SEAT_LIMITS = List.of(
            new TenantSeatLimit(T1, "T1", "Alpha School", 100),
            new TenantSeatLimit(T2, "T2", "Beta Centre", 50),
            new TenantSeatLimit(T3, "T3", "Gamma College", 200),
            new TenantSeatLimit(T4, "T4", "Delta Academy", 10),
            new TenantSeatLimit(T5, "T5", "Epsilon Institute", 400),
            new TenantSeatLimit(T6, "T6", "Zeta Unlimited-ish", 0));

    /** Students per tenant: T1 90%, T2 50%, T3 10%, T4 100%, T5 1%, T6 has students but no seats; GHOST is not a tenant. */
    public static final Map<UUID, Long> STUDENTS = Map.of(T1, 90L, T2, 25L, T3, 20L, T4, 10L, T5, 4L, T6, 7L,
            GHOST, 1000L);

    public static final Instant T1_CREATED = Instant.parse("2026-10-10T01:00:00Z");
    public static final Instant T2_CREATED = Instant.parse("2026-10-05T01:00:00Z");
    public static final Instant T3_CREATED = Instant.parse("2026-09-20T01:00:00Z");
    public static final Instant PLAN_EXPIRY = Instant.parse("2027-03-01T00:00:00Z");

    public static final UUID APP_1 = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    public static final UUID APP_2 = UUID.fromString("b2000000-0000-0000-0000-000000000002");
    public static final UUID TICKET_1 = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    public static final UUID TICKET_2 = UUID.fromString("c2000000-0000-0000-0000-000000000002");

    /** Stubs every facade for the default 30 day window ending at {@link #NOW}. */
    public AdminDashboardFixtures stubHappyPath() {
        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put("CENTER", 8L);
        byType.put("SCHOOL", 12L);
        when(tenancyOverview.platformTenantCounts()).thenReturn(new PlatformTenantCounts(18, 2, byType));
        when(tenancyOverview.countTenantsCreatedBetween(WINDOW_START, NOW)).thenReturn(6L);
        when(tenancyOverview.countTenantsCreatedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(4L);
        when(tenancyOverview.countTenantsCreatedPerRange(anyList())).thenReturn(LongStream.rangeClosed(1, 12).boxed().toList());
        when(tenancyOverview.seatLimits()).thenReturn(SEAT_LIMITS);
        when(tenancyOverview.recentTenants(5)).thenReturn(List.of(
                new RecentTenantView(T1, "T1", "Alpha School", "SCHOOL", "ACTIVE", 100, T1_CREATED),
                new RecentTenantView(T2, "T2", "Beta Centre", "CENTER", "SUSPENDED", 50, T2_CREATED),
                new RecentTenantView(T3, "T3", "Gamma College", "SCHOOL", "ACTIVE", 200, T3_CREATED)));
        when(tenancy.findTenantSummaries(anyCollection())).thenAnswer(invocation -> {
            Map<UUID, TenantSummary> summaries = new HashMap<>();
            for (Object id : (java.util.Collection<?>) invocation.getArgument(0)) {
                summaries.put((UUID) id, new TenantSummary((UUID) id, "Name of " + id, false));
            }
            return summaries;
        });

        when(identity.studentCountsByTenant()).thenReturn(STUDENTS);
        when(attempts.countDistinctActiveStudentsPlatformWide(WINDOW_START, NOW)).thenReturn(300L);
        when(attempts.countDistinctActiveStudentsPlatformWide(PREVIOUS_START, WINDOW_START)).thenReturn(250L);

        when(billing.currentPlanByTenant(anyCollection(), eq(NOW))).thenReturn(
                Map.of(T1, new TenantPlanBrief("Pro Pack", PLAN_EXPIRY)));
        when(billing.pendingApplicationCount()).thenReturn(7L);
        when(billing.countApplicationsReceivedBetween(WINDOW_START, NOW)).thenReturn(5L);
        when(billing.countApplicationsReceivedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(10L);
        when(billing.oldestPendingApplications(5)).thenReturn(List.of(
                new PendingApplicationView(APP_1, "Org One", "SCHOOL", "ORG1", Instant.parse("2026-09-01T00:00:00Z")),
                new PendingApplicationView(APP_2, "Org Two", "CENTER", "ORG2", Instant.parse("2026-09-02T00:00:00Z"))));
        when(billing.licenseCounts(NOW)).thenReturn(new LicenseCounts(40, 15, 3, 9, 6));
        when(billing.countLicensesRedeemedBetween(WINDOW_START, NOW)).thenReturn(8L);
        when(billing.countLicensesRedeemedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(8L);
        when(billing.subscriptionCounts(NOW)).thenReturn(new SubscriptionCounts(33, 5));
        Map<String, Long> current = new LinkedHashMap<>();
        current.put("PAYMENT", 4L);
        current.put("LICENSE_CODE", 3L);
        Map<String, Long> previous = new LinkedHashMap<>();
        previous.put("PAYMENT", 1L);
        previous.put("LICENSE_CODE", 1L);
        when(billing.subscriptionsActivatedBySource(WINDOW_START, NOW)).thenReturn(current);
        when(billing.subscriptionsActivatedBySource(PREVIOUS_START, WINDOW_START)).thenReturn(previous);
        Map<String, Long> planTypes = new LinkedHashMap<>();
        planTypes.put("EXAM_PACKAGE", 5L);
        planTypes.put("STUDENT_CAPACITY", 2L);
        when(billing.planCounts()).thenReturn(new PlanCounts(4, 2, 1, planTypes));

        when(support.platformTicketCounts()).thenReturn(new TicketCounts(11, 4, 2));
        when(support.countOpenedBetween(WINDOW_START, NOW)).thenReturn(9L);
        when(support.countOpenedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(3L);
        when(support.oldestOpenTickets(5)).thenReturn(List.of(
                new OpenTicketBrief(TICKET_1, T1, "BUG", Instant.parse("2026-09-10T00:00:00Z")),
                new OpenTicketBrief(TICKET_2, T1, "CONTENT_COMPLAINT", Instant.parse("2026-09-11T00:00:00Z"))));
        return stubRevenueAndQuestionBank();
    }

    /**
     * Phase 4: deterministic revenue (VND and USD in the current window, VND and EUR in the previous one, so the
     * currency set is the union) and question bank numbers. Callable on its own after a mock reset.
     */
    public AdminDashboardFixtures stubRevenueAndQuestionBank() {
        when(revenueFacade.paidRevenueBetween(WINDOW_START, NOW)).thenReturn(List.of(
                new CurrencyPaidTotal("USD", new BigDecimal("120.00"), 2),
                new CurrencyPaidTotal("VND", new BigDecimal("1500000.50"), 3)));
        when(revenueFacade.paidRevenueBetween(PREVIOUS_START, WINDOW_START)).thenReturn(List.of(
                new CurrencyPaidTotal("EUR", new BigDecimal("50.00"), 1),
                new CurrencyPaidTotal("VND", new BigDecimal("1000000.00"), 2)));
        List<BigDecimal> vnd = new ArrayList<>(Collections.nCopies(12, BigDecimal.ZERO));
        vnd.set(10, new BigDecimal("1000000.00"));
        vnd.set(11, new BigDecimal("1500000.50"));
        List<BigDecimal> usd = new ArrayList<>(Collections.nCopies(12, BigDecimal.ZERO));
        usd.set(11, new BigDecimal("120.00"));
        Map<String, List<BigDecimal>> series = new LinkedHashMap<>();
        series.put("USD", List.copyOf(usd));
        series.put("VND", List.copyOf(vnd));
        when(revenueFacade.paidAmountsPerRange(anyList())).thenReturn(series);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("DRAFT", 20L);
        byStatus.put("PENDING_APPROVAL", 10L);
        byStatus.put("APPROVED", 80L);
        byStatus.put("ARCHIVED", 10L);
        Map<String, Long> bySection = new LinkedHashMap<>();
        bySection.put("LISTENING", 30L);
        bySection.put("SPEAKING", 30L);
        bySection.put("READING", 30L);
        bySection.put("WRITING", 30L);
        Map<String, Long> byPool = new LinkedHashMap<>();
        byPool.put("EXAM", 100L);
        byPool.put("PRACTICE", 20L);
        Map<String, Long> approvedByPool = new LinkedHashMap<>();
        approvedByPool.put("EXAM", 70L);
        approvedByPool.put("PRACTICE", 10L);
        when(itembank.counts()).thenReturn(new QuestionBankCounts(120, byStatus, bySection, byPool, approvedByPool));
        when(itembank.countCreatedBetween(WINDOW_START, NOW)).thenReturn(12L);
        when(itembank.countCreatedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(8L);
        return this;
    }

    // ---- services ----

    public AdminDashboardService service(DashboardCache cache) {
        return service(cache, CLOCK);
    }

    public AdminDashboardService service(DashboardCache cache, Clock clock) {
        return new AdminDashboardService(identity, tenantSections(), commercialSections(), revenueSections(),
                questionBankSections(), new DashboardSectionRunner(), cache, clock);
    }

    public AdminTenantSections tenantSections() {
        return new AdminTenantSections(tenancyOverview, attempts, billing);
    }

    public AdminCommercialSections commercialSections() {
        return new AdminCommercialSections(billing, support, tenancy);
    }

    public AdminRevenueSections revenueSections() {
        return new AdminRevenueSections(revenueFacade);
    }

    public AdminQuestionBankSections questionBankSections() {
        return new AdminQuestionBankSections(itembank);
    }

    /** A cache that always misses and never stores: the loader runs on every call (see {@link HostDashboardFixtures}). */
    public static DashboardCache passThroughCache() {
        return HostDashboardFixtures.passThroughCache();
    }

    // ---- fake Redis ----

    /** In-memory Redis: a real {@link DashboardCache} runs over it so keys, TTLs and JSON payloads are real. */
    public static final class FakeRedis {

        public final Map<String, String> store = new LinkedHashMap<>();
        public final Map<String, Duration> ttls = new LinkedHashMap<>();
        public final StringRedisTemplate template = mock(StringRedisTemplate.class);
        public final ValueOperations<String, String> ops;
        public final List<String> reads = new ArrayList<>();
        public final JsonMapper json;

        @SuppressWarnings("unchecked")
        public FakeRedis(JsonMapper json) {
            this.json = json;
            this.ops = mock(ValueOperations.class);
            when(template.opsForValue()).thenReturn(ops);
            when(ops.get(anyString())).thenAnswer(invocation -> {
                reads.add(invocation.getArgument(0));
                return store.get((String) invocation.getArgument(0));
            });
            doAnswer(invocation -> {
                store.put(invocation.getArgument(0), invocation.getArgument(1));
                ttls.put(invocation.getArgument(0), invocation.getArgument(2));
                return null;
            }).when(ops).set(anyString(), anyString(), any(Duration.class));
        }

        public FakeRedis() {
            this(JsonMapper.builder().findAndAddModules().build());
        }

        public DashboardCache cache() {
            return new DashboardCache(template, json);
        }
    }
}
