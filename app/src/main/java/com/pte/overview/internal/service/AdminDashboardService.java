package com.pte.overview.internal.service;

import com.pte.identity.IdentityOverviewService;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.cache.DashboardCacheKeys;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminDashboardResponse;
import com.pte.overview.internal.dto.response.AdminSectionData.Applications;
import com.pte.overview.internal.dto.response.AdminSectionData.Learners;
import com.pte.overview.internal.dto.response.AdminSectionData.Licenses;
import com.pte.overview.internal.dto.response.AdminSectionData.Plans;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenants;
import com.pte.overview.internal.dto.response.AdminSectionData.Subscriptions;
import com.pte.overview.internal.dto.response.AdminSectionData.Support;
import com.pte.overview.internal.dto.response.AdminSectionData.Tenants;
import com.pte.overview.internal.dto.response.DashboardMeta;
import com.pte.overview.internal.dto.response.DashboardSection;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Composes the platform admin dashboard. For every section the caller's permission is
 * checked first and independently ({@link AdminVisibility}); a section the caller may not
 * see is returned FORBIDDEN without touching the cache or any query. Allowed sections are
 * cached one by one for 60 seconds under a key built from the caller's computed visibility
 * bucket, the window and the schema version (a section failure is never cached, and a
 * cache failure only means computing directly). Each section is isolated by
 * {@link DashboardSectionRunner}. Mutations may show for up to one TTL (documented).
 */
@Service
public class AdminDashboardService {

    private final IdentityOverviewService identity;
    private final AdminTenantSections tenantSections;
    private final AdminCommercialSections commercialSections;
    private final DashboardSectionRunner runner;
    private final DashboardCache cache;
    private final Clock clock;

    public AdminDashboardService(IdentityOverviewService identity, AdminTenantSections tenantSections,
            AdminCommercialSections commercialSections, DashboardSectionRunner runner, DashboardCache cache,
            Clock clock) {
        this.identity = identity;
        this.tenantSections = tenantSections;
        this.commercialSections = commercialSections;
        this.runner = runner;
        this.cache = cache;
        this.clock = clock;
    }

    /** Builds the dashboard for a platform {@code caller}; {@code rawWindowDays} is the unvalidated request value. */
    public AdminDashboardResponse build(CurrentUser caller, String rawWindowDays) {
        DashboardWindow window = DashboardWindow.parse(rawWindowDays, clock);
        AdminVisibility visibility = AdminVisibility.resolve(caller);
        DashboardMeta meta = new DashboardMeta(window.end(), window.days(), window.start(), window.end(),
                OverviewConstants.BUCKET_ZONE);
        Supplier<Map<UUID, Long>> studentCounts = new Memo<>(identity::studentCountsByTenant);
        return new AdminDashboardResponse(meta,
                section(AdminSection.TENANTS, visibility, window, Tenants.class,
                        () -> tenantSections.tenants(window)),
                section(AdminSection.LEARNERS, visibility, window, Learners.class,
                        () -> tenantSections.learners(window, studentCounts)),
                section(AdminSection.RECENT_TENANTS, visibility, window, RecentTenants.class,
                        () -> tenantSections.recentTenants(window, studentCounts)),
                section(AdminSection.APPLICATIONS, visibility, window, Applications.class,
                        () -> commercialSections.applications(window)),
                section(AdminSection.LICENSES, visibility, window, Licenses.class,
                        () -> commercialSections.licenses(window)),
                section(AdminSection.SUBSCRIPTIONS, visibility, window, Subscriptions.class,
                        () -> commercialSections.subscriptions(window)),
                section(AdminSection.PLANS, visibility, window, Plans.class, commercialSections::plans),
                section(AdminSection.SUPPORT, visibility, window, Support.class,
                        () -> commercialSections.support(window)));
    }

    private <T> DashboardSection<T> section(AdminSection section, AdminVisibility visibility, DashboardWindow window,
            Class<T> type, Supplier<T> loader) {
        if (!visibility.allows(section)) {
            return DashboardSection.forbidden();
        }
        String key = DashboardCacheKeys.admin(visibility.bucket(), window.days(), section.key());
        return runner.run(section.key(),
                () -> cache.getOrCompute(key, OverviewConstants.ADMIN_SECTION_TTL, type, loader));
    }
}
