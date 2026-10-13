package com.pte.overview.internal.dto.response;

import com.pte.overview.internal.dto.response.AdminSectionData.Applications;
import com.pte.overview.internal.dto.response.AdminSectionData.Learners;
import com.pte.overview.internal.dto.response.AdminSectionData.Licenses;
import com.pte.overview.internal.dto.response.AdminSectionData.Plans;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenants;
import com.pte.overview.internal.dto.response.AdminSectionData.Subscriptions;
import com.pte.overview.internal.dto.response.AdminSectionData.Support;
import com.pte.overview.internal.dto.response.AdminSectionData.Tenants;

/**
 * Platform admin dashboard aggregate. A section the caller may not see is returned as FORBIDDEN
 * with null data (HTTP stays 200); later releases add sections additively.
 */
public record AdminDashboardResponse(
        DashboardMeta meta,
        DashboardSection<Tenants> tenants,
        DashboardSection<Learners> learners,
        DashboardSection<RecentTenants> recentTenants,
        DashboardSection<Applications> applications,
        DashboardSection<Licenses> licenses,
        DashboardSection<Subscriptions> subscriptions,
        DashboardSection<Plans> plans,
        DashboardSection<Support> support) {
}
