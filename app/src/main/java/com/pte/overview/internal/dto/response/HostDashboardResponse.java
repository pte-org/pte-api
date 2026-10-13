package com.pte.overview.internal.dto.response;

import com.pte.overview.internal.dto.response.HostSectionData.Academics;
import com.pte.overview.internal.dto.response.HostSectionData.ActivityItem;
import com.pte.overview.internal.dto.response.HostSectionData.Attempts;
import com.pte.overview.internal.dto.response.HostSectionData.Billing;
import com.pte.overview.internal.dto.response.HostSectionData.Exams;
import com.pte.overview.internal.dto.response.HostSectionData.Grading;
import com.pte.overview.internal.dto.response.HostSectionData.Learners;
import com.pte.overview.internal.dto.response.HostSectionData.Quota;
import com.pte.overview.internal.dto.response.HostSectionData.Staff;
import com.pte.overview.internal.dto.response.HostSectionData.Subscription;
import com.pte.overview.internal.dto.response.HostSectionData.Support;

import java.util.List;

/** Host (tenant) dashboard aggregate; later releases add sections additively. */
public record HostDashboardResponse(
        DashboardMeta meta,
        DashboardSection<Learners> learners,
        DashboardSection<Quota> quota,
        DashboardSection<Academics> academics,
        DashboardSection<Staff> staff,
        DashboardSection<Exams> exams,
        DashboardSection<Attempts> attempts,
        DashboardSection<Grading> grading,
        DashboardSection<Subscription> subscription,
        DashboardSection<Billing> billing,
        DashboardSection<Support> support,
        DashboardSection<List<ActivityItem>> recentActivity) {
}
