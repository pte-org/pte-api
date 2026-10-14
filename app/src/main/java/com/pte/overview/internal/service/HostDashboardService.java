package com.pte.overview.internal.service;

import com.pte.attempt.AttemptOverviewService;
import com.pte.enrollment.EnrollmentOverviewService;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.identity.IdentityOverviewService;
import com.pte.identity.IdentityOverviewService.StaffCounts;
import com.pte.identity.IdentityOverviewService.StudentStatusCounts;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.cache.DashboardCacheKeys;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.DashboardMeta;
import com.pte.overview.internal.dto.response.HostDashboardResponse;
import com.pte.overview.internal.dto.response.HostSectionData.Academics;
import com.pte.overview.internal.dto.response.HostSectionData.Attempts;
import com.pte.overview.internal.dto.response.HostSectionData.Exams;
import com.pte.overview.internal.dto.response.HostSectionData.Learners;
import com.pte.overview.internal.dto.response.HostSectionData.Quota;
import com.pte.overview.internal.dto.response.HostSectionData.Staff;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.overview.internal.dto.response.MonthlySeries;
import com.pte.session.SessionOverviewService;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Composes the host dashboard from the other modules' public facades. Core counts
 * are computed on every request (cheap indexed counts, so a post-mutation refetch
 * is fresh); only the 12-month series goes through the 30 second cache. Each
 * section is isolated by {@link DashboardSectionRunner}. The tenant id is always
 * the caller's own, supplied by the controller from the token.
 */
@Service
public class HostDashboardService {

    private static final ZoneId ZONE = ZoneId.of(OverviewConstants.BUCKET_ZONE);

    private final IdentityOverviewService identity;
    private final EnrollmentOverviewService enrollment;
    private final SessionOverviewService sessions;
    private final AttemptOverviewService attemptOverview;
    private final TenancyService tenancy;
    private final HostOperationsSections operations;
    private final DashboardSectionRunner runner;
    private final DashboardCache cache;
    private final Clock clock;

    public HostDashboardService(IdentityOverviewService identity, EnrollmentOverviewService enrollment,
            SessionOverviewService sessions, AttemptOverviewService attemptOverview, TenancyService tenancy,
            HostOperationsSections operations, DashboardSectionRunner runner, DashboardCache cache, Clock clock) {
        this.operations = operations;
        this.identity = identity;
        this.enrollment = enrollment;
        this.sessions = sessions;
        this.attemptOverview = attemptOverview;
        this.tenancy = tenancy;
        this.runner = runner;
        this.cache = cache;
        this.clock = clock;
    }

    /** Builds the dashboard for {@code tenantId}; {@code rawWindowDays} is the unvalidated request value. */
    public HostDashboardResponse build(UUID tenantId, String rawWindowDays) {
        DashboardWindow window = DashboardWindow.parse(rawWindowDays, clock);
        DashboardMeta meta = new DashboardMeta(window.end(), window.days(), window.start(), window.end(),
                OverviewConstants.BUCKET_ZONE);
        Supplier<AcademicCounts> academicCounts = new Memo<>(() -> enrollment.academicCounts(tenantId));
        return new HostDashboardResponse(meta,
                runner.run(OverviewConstants.SECTION_LEARNERS, () -> learners(tenantId, window, academicCounts)),
                runner.run(OverviewConstants.SECTION_QUOTA, () -> quota(tenantId)),
                runner.run(OverviewConstants.SECTION_ACADEMICS, () -> academics(tenantId, academicCounts)),
                runner.run(OverviewConstants.SECTION_STAFF, () -> staff(tenantId)),
                runner.run(OverviewConstants.SECTION_EXAMS, () -> exams(tenantId, window)),
                runner.run(OverviewConstants.SECTION_ATTEMPTS, () -> attempts(tenantId, window)),
                runner.run(OverviewConstants.SECTION_GRADING, () -> operations.grading(tenantId, window.days())),
                runner.run(OverviewConstants.SECTION_SUBSCRIPTION, () -> operations.subscription(tenantId)),
                runner.run(OverviewConstants.SECTION_BILLING, () -> operations.billing(tenantId)),
                runner.run(OverviewConstants.SECTION_SUPPORT, () -> operations.support(tenantId)),
                runner.run(OverviewConstants.SECTION_RECENT_ACTIVITY, () -> operations.recentActivity(tenantId)));
    }

    private Learners learners(UUID tenantId, DashboardWindow w, Supplier<AcademicCounts> academicCounts) {
        StudentStatusCounts counts = identity.studentStatusCounts(tenantId);
        AcademicCounts academic = academicCounts.get();
        return new Learners(counts.total(), counts.active(), counts.suspended(),
                TrendCalculator.of(identity.countStudentsCreatedBetween(tenantId, w.start(), w.end()),
                        identity.countStudentsCreatedBetween(tenantId, w.previousStart(), w.start())),
                TrendCalculator.of(attemptOverview.countDistinctActiveStudents(tenantId, w.start(), w.end()),
                        attemptOverview.countDistinctActiveStudents(tenantId, w.previousStart(), w.start())),
                Math.max(0L, counts.total() - academic.assignedLearners()));
    }

    private Quota quota(UUID tenantId) {
        StudentQuota quota = tenancy.getStudentQuota(tenantId);
        return new Quota(quota.current(), quota.limit(), quota.remaining());
    }

    private Academics academics(UUID tenantId, Supplier<AcademicCounts> academicCounts) {
        AcademicCounts academic = academicCounts.get();
        return new Academics(tenancy.countOrganizations(tenantId), academic.programs(), academic.activeClasses());
    }

    private Staff staff(UUID tenantId) {
        StaffCounts staff = identity.activeStaffCounts(tenantId);
        return new Staff(staff.proctors(), staff.examiners());
    }

    private Exams exams(UUID tenantId, DashboardWindow w) {
        return new Exams(sessions.countByStatus(tenantId),
                TrendCalculator.of(sessions.countCreatedBetween(tenantId, w.start(), w.end()),
                        sessions.countCreatedBetween(tenantId, w.previousStart(), w.start())),
                sessions.upcoming(tenantId, w.end(), OverviewConstants.UPCOMING_SESSIONS_LIMIT));
    }

    private Attempts attempts(UUID tenantId, DashboardWindow w) {
        return new Attempts(
                TrendCalculator.of(attemptOverview.countSubmitted(tenantId, w.start(), w.end()),
                        attemptOverview.countSubmitted(tenantId, w.previousStart(), w.start())),
                attemptOverview.countInProgress(tenantId),
                monthlySubmitted(tenantId, w));
    }

    /** Heavy series: 12 calendar-month counts, cached per tenant and window for 30 seconds. */
    private List<MonthlyPoint> monthlySubmitted(UUID tenantId, DashboardWindow w) {
        String key = DashboardCacheKeys.host(tenantId, w.days(), OverviewConstants.SERIES_ATTEMPTS_MONTHLY);
        return cache.getOrCompute(key, OverviewConstants.HOST_SERIES_TTL, MonthlySeries.class,
                () -> computeMonthlySeries(tenantId, w.end())).points();
    }

    private MonthlySeries computeMonthlySeries(UUID tenantId, Instant now) {
        List<Instant> boundaries = MonthBuckets.boundaries(now, ZONE, OverviewConstants.SERIES_MONTHS);
        List<Long> counts = attemptOverview.countSubmittedPerRange(tenantId, boundaries);
        return new MonthlySeries(MonthBuckets.toPoints(now, ZONE, counts));
    }
}
