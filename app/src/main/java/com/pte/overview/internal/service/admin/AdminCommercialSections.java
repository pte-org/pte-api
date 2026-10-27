package com.pte.overview.internal.service.admin;

import com.pte.overview.internal.service.DashboardWindow;
import com.pte.overview.internal.service.TrendCalculator;
import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.LicenseCounts;
import com.pte.billing.BillingOverviewService.PlanCounts;
import com.pte.billing.BillingOverviewService.SubscriptionCounts;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminSectionData.Applications;
import com.pte.overview.internal.dto.response.AdminSectionData.Licenses;
import com.pte.overview.internal.dto.response.AdminSectionData.OldestTicket;
import com.pte.overview.internal.dto.response.AdminSectionData.PendingApplication;
import com.pte.overview.internal.dto.response.AdminSectionData.Plans;
import com.pte.overview.internal.dto.response.AdminSectionData.Subscriptions;
import com.pte.overview.internal.dto.response.AdminSectionData.Support;
import com.pte.support.SupportOverviewService;
import com.pte.support.SupportOverviewService.OpenTicketBrief;
import com.pte.support.SupportOverviewService.TicketCounts;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.TenantSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload builders for the commercial and operations admin sections (applications, licenses,
 * subscriptions, plans, support). License data is counts only: no code value is ever read.
 * Permission checks happen in the caller, before any of these methods run.
 */
@Component
public class AdminCommercialSections {

    private final BillingOverviewService billing;
    private final SupportOverviewService support;
    private final TenancyService tenancy;

    public AdminCommercialSections(BillingOverviewService billing, SupportOverviewService support,
            TenancyService tenancy) {
        this.billing = billing;
        this.support = support;
        this.tenancy = tenancy;
    }

    public Applications applications(DashboardWindow w) {
        List<PendingApplication> oldest = billing
                .oldestPendingApplications(OverviewConstants.OLDEST_APPLICATIONS_LIMIT).stream()
                .map(a -> new PendingApplication(a.publicId(), a.orgName(), a.orgType(), a.requestedCode(),
                        a.receivedAt()))
                .toList();
        return new Applications(billing.pendingApplicationCount(),
                TrendCalculator.of(billing.countApplicationsReceivedBetween(w.start(), w.end()),
                        billing.countApplicationsReceivedBetween(w.previousStart(), w.start())),
                oldest);
    }

    public Licenses licenses(DashboardWindow w) {
        LicenseCounts counts = billing.licenseCounts(w.end());
        return new Licenses(counts.issuedValid(), counts.redeemed(), counts.revoked(), counts.expired(),
                counts.expiringSoon(), BillingOverviewService.LICENSE_EXPIRING_SOON_DAYS,
                TrendCalculator.of(billing.countLicensesRedeemedBetween(w.start(), w.end()),
                        billing.countLicensesRedeemedBetween(w.previousStart(), w.start())));
    }

    public Subscriptions subscriptions(DashboardWindow w) {
        SubscriptionCounts counts = billing.subscriptionCounts(w.end());
        Map<String, Long> current = billing.subscriptionsActivatedBySource(w.start(), w.end());
        Map<String, Long> previous = billing.subscriptionsActivatedBySource(w.previousStart(), w.start());
        return new Subscriptions(counts.active(), counts.expiringSoon(), BillingOverviewService.EXPIRING_SOON_DAYS,
                TrendCalculator.of(sum(current), sum(previous)), current);
    }

    public Plans plans() {
        PlanCounts counts = billing.planCounts();
        return new Plans(counts.active(), counts.draft(), counts.archived(), counts.byType());
    }

    public Support support(DashboardWindow w) {
        TicketCounts counts = support.platformTicketCounts();
        List<OpenTicketBrief> oldest = support.oldestOpenTickets(OverviewConstants.OLDEST_TICKETS_LIMIT);
        Map<UUID, TenantSummary> tenants = tenancy.findTenantSummaries(
                oldest.stream().map(OpenTicketBrief::tenantId).distinct().toList());
        return new Support(counts.open(), counts.inProgress(),
                TrendCalculator.of(support.countOpenedBetween(w.start(), w.end()),
                        support.countOpenedBetween(w.previousStart(), w.start())),
                oldest.stream().map(t -> new OldestTicket(t.publicId(), t.tenantId(),
                        tenants.containsKey(t.tenantId()) ? tenants.get(t.tenantId()).name() : null, t.category(),
                        t.openedAt())).toList());
    }

    private static long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
