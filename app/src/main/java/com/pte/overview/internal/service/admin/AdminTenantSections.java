package com.pte.overview.internal.service.admin;

import com.pte.overview.internal.service.DashboardWindow;
import com.pte.overview.internal.service.MonthBuckets;
import com.pte.overview.internal.service.SeatUtilisationRanking;
import com.pte.overview.internal.service.TrendCalculator;
import com.pte.attempt.AttemptOverviewService;
import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.TenantPlanBrief;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminSectionData.Learners;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenant;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenants;
import com.pte.overview.internal.dto.response.AdminSectionData.Tenants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.tenancy.PlatformTenantCounts;
import com.pte.tenancy.RecentTenantView;
import com.pte.tenancy.TenancyOverviewService;
import com.pte.tenancy.TenantSeatLimit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Payload builders for the tenant-centred admin sections (tenants, learners, recent tenants).
 * The platform-wide grouped student count is passed in as a per-request {@link Memo}, so the
 * heaviest query runs at most once however many sections read it, and recent tenants, plan
 * names and expiries are composed from batched facade calls (never a lookup per tenant).
 * Permission checks happen in the caller, before any of these methods run.
 */
@Component
public class AdminTenantSections {

    private final TenancyOverviewService tenancy;
    private final AttemptOverviewService attempts;
    private final BillingOverviewService billing;

    public AdminTenantSections(TenancyOverviewService tenancy, AttemptOverviewService attempts,
            BillingOverviewService billing) {
        this.tenancy = tenancy;
        this.attempts = attempts;
        this.billing = billing;
    }

    public Tenants tenants(DashboardWindow w) {
        PlatformTenantCounts counts = tenancy.platformTenantCounts();
        List<Instant> boundaries = MonthBuckets.boundaries(w.end(), OverviewConstants.BUCKET_ZONE_ID,
                OverviewConstants.SERIES_MONTHS);
        List<MonthlyPoint> monthlyCreated = MonthBuckets.toPoints(w.end(), OverviewConstants.BUCKET_ZONE_ID,
                tenancy.countTenantsCreatedPerRange(boundaries));
        return new Tenants(counts.total(), counts.active(), counts.suspended(), counts.byOrganizationType(),
                TrendCalculator.of(tenancy.countTenantsCreatedBetween(w.start(), w.end()),
                        tenancy.countTenantsCreatedBetween(w.previousStart(), w.start())),
                monthlyCreated);
    }

    public Learners learners(DashboardWindow w, Supplier<Map<UUID, Long>> studentCounts) {
        Map<UUID, Long> studentsByTenant = studentCounts.get();
        List<TenantSeatLimit> limits = tenancy.seatLimits();
        long totalSeats = limits.stream().mapToLong(TenantSeatLimit::studentLimit).sum();
        long totalStudents = limits.stream().mapToLong(l -> studentsByTenant.getOrDefault(l.publicId(), 0L)).sum();
        return new Learners(totalStudents, totalSeats,
                TrendCalculator.of(attempts.countDistinctActiveStudentsPlatformWide(w.start(), w.end()),
                        attempts.countDistinctActiveStudentsPlatformWide(w.previousStart(), w.start())),
                SeatUtilisationRanking.top(limits, studentsByTenant, OverviewConstants.TOP_UTILISATION_LIMIT));
    }

    public RecentTenants recentTenants(DashboardWindow w, Supplier<Map<UUID, Long>> studentCounts) {
        List<RecentTenantView> recent = tenancy.recentTenants(OverviewConstants.RECENT_TENANTS_LIMIT);
        Map<UUID, TenantPlanBrief> plans = billing.currentPlanByTenant(
                recent.stream().map(RecentTenantView::publicId).toList(), w.end());
        Map<UUID, Long> studentsByTenant = studentCounts.get();
        return new RecentTenants(recent.stream().map(tenant -> {
            TenantPlanBrief plan = plans.get(tenant.publicId());
            return new RecentTenant(tenant.publicId(), tenant.code(), tenant.name(), tenant.organizationType(),
                    tenant.status(), plan == null ? null : plan.planName(),
                    studentsByTenant.getOrDefault(tenant.publicId(), 0L), tenant.studentLimit(),
                    plan == null ? null : plan.expiresAt(), tenant.createdAt());
        }).toList());
    }
}
