package com.pte.tenancy.internal.repository;

import com.pte.tenancy.PlatformTenantCounts;
import com.pte.tenancy.RecentTenantView;
import com.pte.tenancy.TenancyOverviewService;
import com.pte.tenancy.TenantSeatLimit;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform-wide tenant reads behind {@link TenancyOverviewService} on the embedded database: status and organization-type
 * counts, half-open creation ranges (incl. Asia/Ho_Chi_Minh month boundaries that differ from UTC), the newest-first
 * list, the seat-limit projection and the statement counts (bounded, never one per tenant).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TenancyPlatformOverviewQueriesTest {

    // Vietnam month starts expressed in UTC (UTC+7, no DST): 1 Sep / 1 Oct / 1 Nov 00:00 in Asia/Ho_Chi_Minh.
    private static final Instant SEP_START = Instant.parse("2026-08-31T17:00:00Z");
    private static final Instant OCT_START = Instant.parse("2026-09-30T17:00:00Z");
    private static final Instant NOV_START = Instant.parse("2026-10-31T17:00:00Z");

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private TenancyOverviewService service;

    @BeforeEach
    void setUp() {
        service = new TenancyOverviewService(organizations);
    }

    private Organization tenant(String name, String type, OrganizationStatus status, boolean deleted, int seats, Instant createdAt) {
        Organization tenant = new Organization();
        tenant.setCode("c-" + UUID.randomUUID().toString().substring(0, 12));
        tenant.setName(name + "-" + UUID.randomUUID());
        tenant.setOrganizationType(type);
        tenant.setPackageName("starter");
        tenant.setStudentLimit(seats);
        tenant.setStatus(status);
        tenant.setDeleted(deleted);
        organizations.saveAndFlush(tenant);
        if (createdAt != null) {
            // createdAt is @CreationTimestamp and not updatable: rewrite it with a bulk update.
            em.createQuery("update Organization t set t.createdAt = :createdAt where t.id = :id")
                    .setParameter("createdAt", createdAt).setParameter("id", tenant.getId()).executeUpdate();
        }
        em.clear();
        return tenant;
    }

    private Organization active(String type, Instant createdAt) {
        return tenant("t", type, OrganizationStatus.ACTIVE, false, 100, createdAt);
    }

    // ---- platformTenantCounts ----

    @Test
    void countsSplitByStatusAndOrganizationTypeAndExcludeDeletedTenants() {
        tenant("a", "SCHOOL", OrganizationStatus.ACTIVE, false, 10, null);
        tenant("b", "SCHOOL", OrganizationStatus.ACTIVE, false, 10, null);
        tenant("c", "CENTER", OrganizationStatus.ACTIVE, false, 10, null);
        tenant("d", "SCHOOL", OrganizationStatus.SUSPENDED, false, 10, null);
        tenant("e", "UNIVERSITY", OrganizationStatus.SUSPENDED, false, 10, null);
        tenant("f", "SCHOOL", OrganizationStatus.ACTIVE, true, 10, null); // soft-deleted
        tenant("g", "CENTER", OrganizationStatus.SUSPENDED, true, 10, null); // soft-deleted
        tenant("h", "DELETED-ONLY-TYPE", OrganizationStatus.ACTIVE, true, 10, null); // soft-deleted type never listed

        PlatformTenantCounts counts = service.platformTenantCounts();

        assertThat(counts.active()).isEqualTo(3);
        assertThat(counts.suspended()).isEqualTo(2);
        assertThat(counts.total()).isEqualTo(5);
        // SCHOOL: two active + one suspended; the soft-deleted SCHOOL and the deleted-only type are not listed
        assertThat(counts.byOrganizationType()).containsOnly(java.util.Map.entry("CENTER", 1L),
                java.util.Map.entry("SCHOOL", 3L), java.util.Map.entry("UNIVERSITY", 1L));
        assertThat(counts.byOrganizationType().keySet()).containsExactly("CENTER", "SCHOOL", "UNIVERSITY");
    }

    @Test
    void anEmptyPlatformHasZeroCountsAndNoTypes() {
        PlatformTenantCounts counts = service.platformTenantCounts();

        assertThat(counts.total()).isZero();
        assertThat(counts.byOrganizationType()).isEmpty();
    }

    @Test
    void theStatusAndTypeCountsAreTwoGroupedStatementsNotOnePerTenant() {
        for (int i = 0; i < 12; i++) {
            active(i % 2 == 0 ? "SCHOOL" : "CENTER", null);
        }
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        PlatformTenantCounts counts = service.platformTenantCounts();

        assertThat(counts.total()).isEqualTo(12);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    // ---- countTenantsCreatedBetween ----

    @Test
    void createdBetweenIsHalfOpenAndSkipsDeletedTenants() {
        Instant from = Instant.parse("2026-09-13T03:00:00Z");
        Instant to = Instant.parse("2026-10-13T03:00:00Z");
        active("SCHOOL", from); // lower bound included
        active("SCHOOL", to.minusSeconds(1));
        active("SCHOOL", Instant.parse("2026-09-20T00:00:00Z"));
        active("SCHOOL", to); // upper bound excluded
        active("SCHOOL", from.minusSeconds(1)); // before
        tenant("x", "SCHOOL", OrganizationStatus.ACTIVE, true, 5, Instant.parse("2026-09-20T00:00:00Z")); // deleted
        tenant("y", "SCHOOL", OrganizationStatus.SUSPENDED, false, 5, Instant.parse("2026-09-21T00:00:00Z")); // suspended counts

        assertThat(service.countTenantsCreatedBetween(from, to)).isEqualTo(4);
    }

    @Test
    void adjacentRangesPartitionTheTenantsWithoutDoubleCounting() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant middle = Instant.parse("2026-09-16T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");
        active("SCHOOL", from);
        active("SCHOOL", middle); // exactly on the shared boundary
        active("SCHOOL", to.minusSeconds(1));

        long first = service.countTenantsCreatedBetween(from, middle);
        long second = service.countTenantsCreatedBetween(middle, to);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(first + second).isEqualTo(service.countTenantsCreatedBetween(from, to));
    }

    // ---- countTenantsCreatedPerRange: Asia/Ho_Chi_Minh months ----

    @Test
    void perRangeBucketsAnInstantThatIsNextMonthInVietnamButThisMonthInUtcIntoTheVietnamMonth() {
        // 2026-09-30T20:00Z is 03:00 on 1 October in Vietnam but still 30 September in UTC.
        active("SCHOOL", Instant.parse("2026-09-30T20:00:00Z"));
        active("SCHOOL", Instant.parse("2026-09-30T16:59:59Z")); // 23:59:59 on 30 September in Vietnam
        active("SCHOOL", OCT_START); // exactly the first instant of October in Vietnam
        active("SCHOOL", NOV_START.minusSeconds(1)); // last second of October in Vietnam
        active("SCHOOL", NOV_START); // first instant of November
        active("SCHOOL", SEP_START); // first instant of September

        List<Long> counts = service.countTenantsCreatedPerRange(List.of(SEP_START, OCT_START, NOV_START));

        assertThat(counts).containsExactly(2L, 3L); // September: SEP_START + 16:59:59Z; October: 3 rows; November is out
    }

    @Test
    void perRangeReturnsOneCountPerConsecutiveRangeAndTheSumEqualsTheWholeRange() {
        Instant b3 = Instant.parse("2026-11-30T17:00:00Z");
        active("SCHOOL", SEP_START.plusSeconds(10));
        active("SCHOOL", OCT_START.plusSeconds(10));
        active("SCHOOL", OCT_START.plusSeconds(20));
        active("SCHOOL", NOV_START.plusSeconds(10));
        active("SCHOOL", b3); // outside every range

        List<Long> counts = service.countTenantsCreatedPerRange(List.of(SEP_START, OCT_START, NOV_START, b3));

        assertThat(counts).containsExactly(1L, 2L, 1L);
        assertThat(counts.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(service.countTenantsCreatedBetween(SEP_START, b3));
    }

    @Test
    void perRangeExcludesDeletedTenantsAndFewerThanTwoBoundariesIsEmpty() {
        tenant("x", "SCHOOL", OrganizationStatus.ACTIVE, true, 5, OCT_START.plusSeconds(5));
        active("SCHOOL", OCT_START.plusSeconds(5));

        assertThat(service.countTenantsCreatedPerRange(List.of(OCT_START, NOV_START))).containsExactly(1L);
        assertThat(service.countTenantsCreatedPerRange(null)).isEmpty();
        assertThat(service.countTenantsCreatedPerRange(List.of())).isEmpty();
        assertThat(service.countTenantsCreatedPerRange(List.of(OCT_START))).isEmpty();
    }

    // ---- recentTenants ----

    @Test
    void recentTenantsAreNewestFirstBoundedAndSkipDeleted() {
        Organization oldest = active("SCHOOL", Instant.parse("2026-01-01T00:00:00Z"));
        Organization middle = active("CENTER", Instant.parse("2026-05-01T00:00:00Z"));
        Organization newest = active("SCHOOL", Instant.parse("2026-10-01T00:00:00Z"));
        tenant("gone", "SCHOOL", OrganizationStatus.ACTIVE, true, 5, Instant.parse("2026-12-01T00:00:00Z")); // newer but deleted

        List<RecentTenantView> two = service.recentTenants(2);
        List<RecentTenantView> all = service.recentTenants(10);

        assertThat(two).extracting(RecentTenantView::publicId).containsExactly(newest.getPublicId(), middle.getPublicId());
        assertThat(all).extracting(RecentTenantView::publicId)
                .containsExactly(newest.getPublicId(), middle.getPublicId(), oldest.getPublicId());
    }

    @Test
    void recentTenantsCarryTheFieldsTheDashboardNeedsAndTheStatusAsAString() {
        Organization suspended = tenant("s", "UNIVERSITY", OrganizationStatus.SUSPENDED, false, 321, Instant.parse("2026-10-01T00:00:00Z"));

        RecentTenantView view = service.recentTenants(1).get(0);

        assertThat(view.publicId()).isEqualTo(suspended.getPublicId());
        assertThat(view.code()).isEqualTo(suspended.getCode());
        assertThat(view.name()).isEqualTo(suspended.getName());
        assertThat(view.organizationType()).isEqualTo("UNIVERSITY");
        assertThat(view.status()).isEqualTo("SUSPENDED");
        assertThat(view.studentLimit()).isEqualTo(321);
        assertThat(view.createdAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void tenantsCreatedAtTheSameInstantAreOrderedByIdDescendingForStability() {
        Instant same = Instant.parse("2026-10-01T00:00:00Z");
        Organization first = active("SCHOOL", same);
        Organization second = active("SCHOOL", same);
        Organization third = active("SCHOOL", same);

        assertThat(service.recentTenants(3)).extracting(RecentTenantView::publicId)
                .containsExactly(third.getPublicId(), second.getPublicId(), first.getPublicId());
    }

    @Test
    void aNonPositiveLimitReturnsNothingWithoutQuerying() {
        active("SCHOOL", null);
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.recentTenants(0)).isEmpty();
        assertThat(service.recentTenants(-3)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void recentTenantsIsOneBoundedStatementRegardlessOfTheNumberOfTenants() {
        for (int i = 0; i < 15; i++) {
            active("SCHOOL", Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i));
        }
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<RecentTenantView> recent = service.recentTenants(5);

        assertThat(recent).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    // ---- seatLimits ----

    @Test
    void seatLimitsListEveryLiveTenantOnceWithItsConfiguredLimit() {
        Organization a = tenant("a", "SCHOOL", OrganizationStatus.ACTIVE, false, 100, null);
        Organization b = tenant("b", "CENTER", OrganizationStatus.SUSPENDED, false, 0, null);
        tenant("gone", "SCHOOL", OrganizationStatus.ACTIVE, true, 900, null);

        List<TenantSeatLimit> limits = service.seatLimits();

        assertThat(limits).hasSize(2);
        assertThat(limits).extracting(TenantSeatLimit::publicId).containsExactlyInAnyOrder(a.getPublicId(), b.getPublicId());
        assertThat(limits).filteredOn(l -> l.publicId().equals(a.getPublicId())).singleElement().satisfies(l -> {
            assertThat(l.code()).isEqualTo(a.getCode());
            assertThat(l.name()).isEqualTo(a.getName());
            assertThat(l.studentLimit()).isEqualTo(100);
        });
        assertThat(limits).filteredOn(l -> l.publicId().equals(b.getPublicId())).singleElement()
                .extracting(TenantSeatLimit::studentLimit).isEqualTo(0);
    }

    @Test
    void seatLimitsAreOneProjectionStatementWithoutLoadingEntities() {
        for (int i = 0; i < 10; i++) {
            active("SCHOOL", null);
        }
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<TenantSeatLimit> limits = service.seatLimits();

        assertThat(limits).hasSize(10);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }
}
