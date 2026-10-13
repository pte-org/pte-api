package com.pte.overview.internal;

import com.pte.attempt.AttemptOverviewService;
import com.pte.enrollment.EnrollmentOverviewService;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.identity.IdentityOverviewService;
import com.pte.identity.IdentityOverviewService.StaffCounts;
import com.pte.identity.IdentityOverviewService.StudentStatusCounts;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.service.DashboardSectionRunner;
import com.pte.billing.BillingOverviewService;
import com.pte.billing.CurrentSubscriptionView;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.overview.internal.service.HostDashboardService;
import com.pte.overview.internal.service.HostOperationsSections;
import com.pte.reporting.ReportingOverviewService;
import com.pte.scoring.ScoringOverviewService;
import com.pte.session.SessionOverviewService;
import com.pte.shared.audit.AuditLogService;
import com.pte.support.SupportOverviewService;
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

    public final ScoringOverviewService scoring = mock(ScoringOverviewService.class);
    public final ReportingOverviewService reporting = mock(ReportingOverviewService.class);
    public final BillingOverviewService billing = mock(BillingOverviewService.class);
    public final SupportOverviewService support = mock(SupportOverviewService.class);
    public final AuditLogService audit = mock(AuditLogService.class);

    public static final UUID SUBSCRIPTION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    public static final UUID ACTOR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    public static final UUID AUDIT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final Instant NEAREST_EXPIRY = NOW.plus(Duration.ofDays(12)).plusSeconds(3600);

    /**
     * Named fixture "tenant A": 5 pending / 2 AI / 1 failed answers, 3 closed sessions awaiting finalisation,
     * 4 reports unpublished, one subscription expiring in 12 days, 2 pending orders, 3/2/1 tickets, 2 audit entries.
     * Tenant B (see {@link #stubOperationsOfTenantB}) uses visibly different numbers and names.
     */
    public HostDashboardFixtures stubOperations(UUID tenant) {
        when(scoring.backlog(tenant)).thenReturn(new ScoringOverviewService.ScoringBacklog(5, 2, 1, 3));
        when(reporting.countAwaitingPublication(tenant)).thenReturn(4L);
        CurrentSubscriptionView view = new CurrentSubscriptionView(SUBSCRIPTION_ID, "Tenant A Exam Pack",
                "EXAM_PACKAGE", NOW.minus(Duration.ofDays(100)), NEAREST_EXPIRY, "ACTIVE");
        when(billing.subscriptionSummary(tenant)).thenReturn(
                new BillingOverviewService.SubscriptionSummary(List.of(view), NEAREST_EXPIRY, 12L, true));
        when(billing.pendingOrderCount(tenant)).thenReturn(2L);
        when(support.ticketCounts(tenant)).thenReturn(new SupportOverviewService.TicketCounts(3, 2, 1));
        when(audit.recent(tenant, 8)).thenReturn(List.of(
                new AuditLogResponse(AUDIT_ID, ACTOR_ID, "CLASS", "agg-1", "CLASS_CREATED", "SECRET summary text",
                        NOW.minusSeconds(60)),
                new AuditLogResponse(UUID.randomUUID(), ACTOR_ID, "PROGRAM", "agg-2", "PROGRAM_UPDATED",
                        "another summary", NOW.minusSeconds(120))));
        return this;
    }

    public HostDashboardFixtures stubOperationsOfTenantB(UUID tenant) {
        when(scoring.backlog(tenant)).thenReturn(new ScoringOverviewService.ScoringBacklog(70707, 80808, 90909, 60606));
        when(reporting.countAwaitingPublication(tenant)).thenReturn(50505L);
        CurrentSubscriptionView view = new CurrentSubscriptionView(UUID.randomUUID(), "TENANT-B-SECRET-PLAN",
                "EXAM_PACKAGE", NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(300)), "ACTIVE");
        when(billing.subscriptionSummary(tenant)).thenReturn(new BillingOverviewService.SubscriptionSummary(
                List.of(view), NOW.plus(Duration.ofDays(300)), 300L, false));
        when(billing.pendingOrderCount(tenant)).thenReturn(40404L);
        when(support.ticketCounts(tenant)).thenReturn(new SupportOverviewService.TicketCounts(30303, 20202, 10101));
        when(audit.recent(tenant, 8)).thenReturn(List.of(new AuditLogResponse(UUID.randomUUID(), UUID.randomUUID(),
                "TENANT-B-SECRET-TYPE", "x", "TENANT_B_ACTION", "x", NOW)));
        return this;
    }

    /** Core fixtures plus every Phase 2 facade stubbed for {@code tenant} (all eleven sections can be OK). */
    public HostDashboardFixtures stubFullPath(UUID tenant) {
        return stubHappyPath(tenant).stubOperations(tenant);
    }

    public HostDashboardService service(DashboardCache cache) {
        HostOperationsSections operations = new HostOperationsSections(scoring, reporting, billing, support, audit,
                cache);
        return new HostDashboardService(identity, enrollment, sessions, attempts, tenancy,
                operations, new DashboardSectionRunner(), cache, CLOCK);
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
