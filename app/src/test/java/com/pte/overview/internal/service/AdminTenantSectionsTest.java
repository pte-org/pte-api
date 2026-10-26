package com.pte.overview.internal.service;

import com.pte.overview.internal.service.admin.AdminTenantSections;
import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.dto.response.AdminSectionData.Learners;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenant;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenants;
import com.pte.overview.internal.dto.response.AdminSectionData.SeatUtilisation;
import com.pte.overview.internal.dto.response.AdminSectionData.Tenants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.overview.internal.dto.response.TrendValue;
import com.pte.tenancy.PlatformTenantCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static com.pte.overview.internal.AdminDashboardFixtures.GHOST;
import static com.pte.overview.internal.AdminDashboardFixtures.NOW;
import static com.pte.overview.internal.AdminDashboardFixtures.PLAN_EXPIRY;
import static com.pte.overview.internal.AdminDashboardFixtures.STUDENTS;
import static com.pte.overview.internal.AdminDashboardFixtures.T1;
import static com.pte.overview.internal.AdminDashboardFixtures.T1_CREATED;
import static com.pte.overview.internal.AdminDashboardFixtures.T2;
import static com.pte.overview.internal.AdminDashboardFixtures.T2_CREATED;
import static com.pte.overview.internal.AdminDashboardFixtures.T3;
import static com.pte.overview.internal.AdminDashboardFixtures.T3_CREATED;
import static com.pte.overview.internal.AdminDashboardFixtures.T4;
import static com.pte.overview.internal.AdminDashboardFixtures.T5;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Composition of the tenants, learners and recent tenants sections from the (mocked) root facades,
 * including the Asia/Ho_Chi_Minh month series at its boundaries with a fixed clock.
 */
class AdminTenantSectionsTest {

    private AdminDashboardFixtures fx;
    private AdminTenantSections sections;
    private DashboardWindow window;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
        sections = fx.tenantSections();
        window = DashboardWindow.of(30, NOW);
    }

    private static Supplier<Map<UUID, Long>> counts(Map<UUID, Long> map) {
        return () -> map;
    }

    // ---- tenants ----

    @Test
    void tenantTotalsAreActivePlusSuspendedAndOrganizationTypesPassThrough() {
        Tenants tenants = sections.tenants(window);

        assertThat(tenants.total()).isEqualTo(20);
        assertThat(tenants.active()).isEqualTo(18);
        assertThat(tenants.suspended()).isEqualTo(2);
        assertThat(tenants.byOrganizationType()).containsEntry("SCHOOL", 12L).containsEntry("CENTER", 8L);
    }

    @Test
    void newInWindowIsATrendOfTheCurrentAgainstThePreviousRange() {
        Tenants tenants = sections.tenants(window);

        assertThat(tenants.newInWindow()).isEqualTo(new TrendValue(6, 4, 50.0));
    }

    @Test
    void aZeroPreviousWindowGivesNoInventedPercentage() {
        when(fx.tenancyOverview.countTenantsCreatedBetween(window.previousStart(), window.start())).thenReturn(0L);

        assertThat(sections.tenants(window).newInWindow()).isEqualTo(new TrendValue(6, 0, null));
    }

    @Test
    void theGrowthSeriesHasTwelveMonthsOldestFirstWithOnlyTheLastOnePartial() {
        Tenants tenants = sections.tenants(window);

        assertThat(tenants.monthlyCreated()).hasSize(12);
        assertThat(tenants.monthlyCreated().get(0)).isEqualTo(new MonthlyPoint("2025-11", 1, false));
        assertThat(tenants.monthlyCreated().get(11)).isEqualTo(new MonthlyPoint("2026-10", 12, true));
        assertThat(tenants.monthlyCreated().subList(0, 11)).noneMatch(MonthlyPoint::partial);
        assertThat(tenants.monthlyCreated()).extracting(MonthlyPoint::count)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 12).boxed().toList());
    }

    @SuppressWarnings("unchecked")
    private List<Instant> boundariesFor(Instant now) {
        DashboardWindow w = DashboardWindow.of(30, now);
        when(fx.tenancyOverview.countTenantsCreatedPerRange(anyList())).thenReturn(LongStream.range(0, 12).boxed().toList());
        sections.tenants(w);
        ArgumentCaptor<List<Instant>> captor = ArgumentCaptor.forClass(List.class);
        verify(fx.tenancyOverview, org.mockito.Mockito.atLeastOnce()).countTenantsCreatedPerRange(captor.capture());
        return captor.getValue();
    }

    @Test
    void anInstantThatIsNextMonthInVietnamButStillThisMonthInUtcBucketsIntoTheVietnamMonth() {
        // 2026-10-31T18:00Z is 01:00 on 1 November in Asia/Ho_Chi_Minh (UTC+7) but still 31 October in UTC.
        Instant now = Instant.parse("2026-10-31T18:00:00Z");

        List<Instant> boundaries = boundariesFor(now);
        Tenants tenants = sections.tenants(DashboardWindow.of(30, now));

        assertThat(boundaries).hasSize(13);
        // current (partial) month is November in Vietnam: it starts at 2026-10-31T17:00Z, not 2026-10-31T00:00Z
        assertThat(boundaries.get(11)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(boundaries.get(12)).isEqualTo(Instant.parse("2026-11-30T17:00:00Z"));
        assertThat(boundaries.get(0)).isEqualTo(Instant.parse("2025-11-30T17:00:00Z"));
        assertThat(tenants.monthlyCreated()).hasSize(12);
        assertThat(tenants.monthlyCreated().get(0).month()).isEqualTo("2025-12");
        assertThat(tenants.monthlyCreated().get(11).month()).isEqualTo("2026-11");
        assertThat(tenants.monthlyCreated().get(11).partial()).isTrue();
    }

    @Test
    void oneSecondBeforeTheVietnamMonthEndsTheCurrentMonthIsStillTheOldOne() {
        Instant now = Instant.parse("2026-10-31T16:59:59Z"); // 23:59:59 on 31 October in Vietnam

        Tenants tenants = sections.tenants(DashboardWindow.of(30, now));

        assertThat(tenants.monthlyCreated().get(11).month()).isEqualTo("2026-10");
        assertThat(tenants.monthlyCreated().get(0).month()).isEqualTo("2025-11");
    }

    @Test
    void exactlyOnTheVietnamMonthStartTheNewMonthBegins() {
        Instant now = Instant.parse("2026-09-30T17:00:00Z"); // 00:00 on 1 October in Vietnam

        Tenants tenants = sections.tenants(DashboardWindow.of(30, now));

        assertThat(tenants.monthlyCreated().get(11).month()).isEqualTo("2026-10");
        assertThat(boundariesFor(now).get(11)).isEqualTo(now);
    }

    @Test
    void theSeriesCrossesTheYearBoundaryInVietnamTimeWithTwelveDistinctMonths() {
        Instant now = Instant.parse("2025-12-31T20:00:00Z"); // 03:00 on 1 January 2026 in Vietnam

        Tenants tenants = sections.tenants(DashboardWindow.of(30, now));

        assertThat(tenants.monthlyCreated()).extracting(MonthlyPoint::month).containsExactly("2025-02", "2025-03",
                "2025-04", "2025-05", "2025-06", "2025-07", "2025-08", "2025-09", "2025-10", "2025-11", "2025-12",
                "2026-01");
    }

    @Test
    void theBoundariesAreContiguousAscendingAndOneMoreThanTheMonthCount() {
        List<Instant> boundaries = boundariesFor(NOW);

        assertThat(boundaries).hasSize(13).isSorted().doesNotHaveDuplicates();
        assertThat(boundaries.get(0)).isEqualTo(Instant.parse("2025-10-31T17:00:00Z"));
        assertThat(boundaries.get(12)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
    }

    // ---- learners ----

    @Test
    void learnersTotalsJoinTheGroupedCountsWithTheSeatLimitsInMemory() {
        Learners learners = sections.learners(window, counts(STUDENTS));

        assertThat(learners.totalSeats()).isEqualTo(760);
        // 90 + 25 + 20 + 10 + 4 + 7: the ghost tenant (no seat limit row) is not counted
        assertThat(learners.totalStudents()).isEqualTo(156);
        assertThat(STUDENTS).containsKey(GHOST);
    }

    @Test
    void activeLearnersIsAPlatformWideDistinctStudentTrend() {
        Learners learners = sections.learners(window, counts(STUDENTS));

        assertThat(learners.activeLearners()).isEqualTo(new TrendValue(300, 250, 20.0));
        verify(fx.attempts).countDistinctActiveStudentsPlatformWide(window.start(), window.end());
        verify(fx.attempts).countDistinctActiveStudentsPlatformWide(window.previousStart(), window.start());
        verify(fx.attempts, never()).countDistinctActiveStudents(eq(T1), eq(window.start()), eq(window.end()));
    }

    @Test
    void topUtilisationIsTheFullestFiveSkippingTenantsWithoutSeats() {
        Learners learners = sections.learners(window, counts(STUDENTS));

        assertThat(learners.topUtilisation()).extracting(SeatUtilisation::tenantId)
                .containsExactly(T4, T1, AdminDashboardFixtures.T2, T3, T5);
        assertThat(learners.topUtilisation()).extracting(SeatUtilisation::utilisationPct)
                .containsExactly(100.0, 90.0, 50.0, 10.0, 1.0);
        assertThat(learners.topUtilisation()).extracting(SeatUtilisation::tenantId)
                .doesNotContain(AdminDashboardFixtures.T6);
        assertThat(learners.topUtilisation().get(0)).isEqualTo(
                new SeatUtilisation(T4, "T4", "Delta Academy", 10, 10, 100.0));
    }

    @Test
    void learnersUseTheMemoisedGroupedCountOnceAndNeverAskForAPerTenantQuota() {
        Supplier<Map<UUID, Long>> memo = new Memo<>(fx.identity::studentCountsByTenant);

        sections.learners(window, memo);
        sections.recentTenants(window, memo);

        verify(fx.identity, times(1)).studentCountsByTenant();
        verify(fx.tenancy, never()).getStudentQuota(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anEmptyPlatformHasZeroTotalsAndNoUtilisationRows() {
        when(fx.tenancyOverview.seatLimits()).thenReturn(List.of());

        Learners learners = sections.learners(window, counts(Map.of()));

        assertThat(learners.totalSeats()).isZero();
        assertThat(learners.totalStudents()).isZero();
        assertThat(learners.topUtilisation()).isEmpty();
    }

    // ---- recent tenants ----

    @Test
    void recentTenantsCarryPlanNameStudentCountSeatLimitAndExpiryFromTheBatchedFacades() {
        RecentTenants recent = sections.recentTenants(window, counts(STUDENTS));

        assertThat(recent.items()).hasSize(3);
        assertThat(recent.items().get(0)).isEqualTo(new RecentTenant(T1, "T1", "Alpha School", "SCHOOL", "ACTIVE",
                "Pro Pack", 90, 100, PLAN_EXPIRY, T1_CREATED));
    }

    @Test
    void aTenantWithoutAUsableSubscriptionHasNullPlanAndExpiry() {
        RecentTenants recent = sections.recentTenants(window, counts(STUDENTS));

        RecentTenant beta = recent.items().get(1);
        assertThat(beta.tenantId()).isEqualTo(T2);
        assertThat(beta.planName()).isNull();
        assertThat(beta.subscriptionExpiresAt()).isNull();
        assertThat(beta.status()).isEqualTo("SUSPENDED");
        assertThat(beta.createdAt()).isEqualTo(T2_CREATED);
    }

    @Test
    void aTenantWithoutStudentsHasZeroStudentCountAndKeepsItsSeatLimit() {
        RecentTenants recent = sections.recentTenants(window, counts(Map.of(T1, 5L)));

        assertThat(recent.items().get(2)).satisfies(row -> {
            assertThat(row.tenantId()).isEqualTo(T3);
            assertThat(row.studentCount()).isZero();
            assertThat(row.seatLimit()).isEqualTo(200);
            assertThat(row.createdAt()).isEqualTo(T3_CREATED);
        });
    }

    @Test
    void recentTenantsKeepTheFacadeOrderNewestFirst() {
        assertThat(sections.recentTenants(window, counts(STUDENTS)).items()).extracting(RecentTenant::tenantId)
                .containsExactly(T1, T2, T3);
    }

    @Test
    void plansAreResolvedInOneBatchForAllRecentTenantsAtTheWindowEnd() {
        sections.recentTenants(window, counts(STUDENTS));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<UUID>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(fx.billing, times(1)).currentPlanByTenant(ids.capture(), eq(window.end()));
        assertThat(ids.getValue()).containsExactly(T1, T2, T3);
        verify(fx.tenancyOverview).recentTenants(5);
    }

    @Test
    void noRecentTenantsMeansAnEmptyListAndNoPerTenantWork() {
        when(fx.tenancyOverview.recentTenants(5)).thenReturn(List.of());
        when(fx.billing.currentPlanByTenant(anyCollection(), eq(NOW))).thenReturn(Map.of());

        assertThat(sections.recentTenants(window, counts(STUDENTS)).items()).isEmpty();
        verify(fx.billing, times(1)).currentPlanByTenant(anyCollection(), eq(NOW));
    }

    @Test
    void thePlatformTenantCountsRecordDerivesTheTotal() {
        PlatformTenantCounts counts = new PlatformTenantCounts(3, 4, Map.of());

        assertThat(counts.total()).isEqualTo(7);
    }
}
