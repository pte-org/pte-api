package com.pte.overview.internal;

import com.pte.attempt.AttemptOverviewService;
import com.pte.enrollment.EnrollmentOverviewService;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.identity.IdentityOverviewService;
import com.pte.identity.IdentityOverviewService.StaffCounts;
import com.pte.identity.IdentityOverviewService.StudentStatusCounts;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.service.DashboardSectionRunner;
import com.pte.overview.internal.service.HostDashboardService;
import com.pte.session.SessionOverviewService;
import com.pte.session.UpcomingSessionView;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenancyService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared test fixture: mocked public facades with deterministic numbers, a fixed
 * clock and a pass-through cache. The default 30 day window ends at {@link #NOW}
 * (10:00 on 2026-10-13 in Asia/Ho_Chi_Minh).
 */
public final class HostDashboardFixtures {

    public static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final Instant WINDOW_START = NOW.minus(Duration.ofDays(30));
    public static final Instant PREVIOUS_START = NOW.minus(Duration.ofDays(60));

    public final IdentityOverviewService identity = mock(IdentityOverviewService.class);
    public final EnrollmentOverviewService enrollment = mock(EnrollmentOverviewService.class);
    public final SessionOverviewService sessions = mock(SessionOverviewService.class);
    public final AttemptOverviewService attempts = mock(AttemptOverviewService.class);
    public final TenancyService tenancy = mock(TenancyService.class);

    public static final UpcomingSessionView UPCOMING = new UpcomingSessionView(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), "Mock test A", "SCHEDULED",
            NOW.plusSeconds(3600), NOW.plusSeconds(7200), 12L, 30);

    /** Stubs every facade for {@code tenant} and the default 30 day window. */
    public HostDashboardFixtures stubHappyPath(UUID tenant) {
        when(identity.studentStatusCounts(tenant)).thenReturn(new StudentStatusCounts(100, 80, 20));
        when(identity.countStudentsCreatedBetween(tenant, WINDOW_START, NOW)).thenReturn(12L);
        when(identity.countStudentsCreatedBetween(tenant, PREVIOUS_START, WINDOW_START)).thenReturn(8L);
        when(identity.activeStaffCounts(tenant)).thenReturn(new StaffCounts(5, 6));

        when(enrollment.academicCounts(tenant)).thenReturn(new AcademicCounts(4, 7, 70));

        when(tenancy.getStudentQuota(tenant)).thenReturn(new StudentQuota(100, 500));
        when(tenancy.countOrganizations(tenant)).thenReturn(3L);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("DRAFT", 1L);
        byStatus.put("SCHEDULED", 2L);
        byStatus.put("OPEN", 1L);
        when(sessions.countByStatus(tenant)).thenReturn(byStatus);
        when(sessions.countCreatedBetween(tenant, WINDOW_START, NOW)).thenReturn(6L);
        when(sessions.countCreatedBetween(tenant, PREVIOUS_START, WINDOW_START)).thenReturn(3L);
        when(sessions.upcoming(eq(tenant), eq(NOW), eq(5))).thenReturn(List.of(UPCOMING));

        when(attempts.countSubmitted(tenant, WINDOW_START, NOW)).thenReturn(40L);
        when(attempts.countSubmitted(tenant, PREVIOUS_START, WINDOW_START)).thenReturn(50L);
        when(attempts.countInProgress(tenant)).thenReturn(4L);
        when(attempts.countDistinctActiveStudents(tenant, WINDOW_START, NOW)).thenReturn(30L);
        when(attempts.countDistinctActiveStudents(tenant, PREVIOUS_START, WINDOW_START)).thenReturn(0L);
        when(attempts.countSubmittedPerRange(eq(tenant), any())).thenReturn(
                LongStream.rangeClosed(1, 12).boxed().toList());
        return this;
    }

    public HostDashboardService service(DashboardCache cache) {
        return new HostDashboardService(identity, enrollment, sessions, attempts, tenancy,
                new DashboardSectionRunner(), cache, CLOCK);
    }

    /** A cache that always misses: the loader runs on every call. */
    @SuppressWarnings("unchecked")
    public static DashboardCache passThroughCache() {
        DashboardCache cache = mock(DashboardCache.class);
        when(cache.getOrCompute(anyString(), any(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());
        return cache;
    }
}
