package com.pte.overview.internal.service;

import com.pte.billing.BillingOverviewService;
import com.pte.billing.BillingOverviewService.SubscriptionSummary;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.cache.DashboardCacheKeys;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.HostSectionData.ActivityItem;
import com.pte.overview.internal.dto.response.HostSectionData.Billing;
import com.pte.overview.internal.dto.response.HostSectionData.Grading;
import com.pte.overview.internal.dto.response.HostSectionData.Subscription;
import com.pte.overview.internal.dto.response.HostSectionData.Support;
import com.pte.reporting.ReportingOverviewService;
import com.pte.scoring.ScoringOverviewService;
import com.pte.scoring.ScoringOverviewService.ScoringBacklog;
import com.pte.shared.audit.AuditLogService;
import com.pte.support.SupportOverviewService;
import com.pte.support.SupportOverviewService.TicketCounts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Payload builders for the commercial and operations sections of the host
 * dashboard. Only the grading backlog is cached (30 seconds, per tenant); the
 * reports-awaiting count, subscription, order, support and activity data are read
 * on every request. The tenant id is always supplied by the caller from the token.
 */
@Component
public class HostOperationsSections {

    private final ScoringOverviewService scoring;
    private final ReportingOverviewService reporting;
    private final BillingOverviewService billing;
    private final SupportOverviewService support;
    private final AuditLogService audit;
    private final DashboardCache cache;

    public HostOperationsSections(ScoringOverviewService scoring, ReportingOverviewService reporting,
            BillingOverviewService billing, SupportOverviewService support, AuditLogService audit,
            DashboardCache cache) {
        this.scoring = scoring;
        this.reporting = reporting;
        this.billing = billing;
        this.support = support;
        this.audit = audit;
        this.cache = cache;
    }

    public Grading grading(UUID tenantId, int windowDays) {
        String key = DashboardCacheKeys.host(tenantId, windowDays, OverviewConstants.CACHE_SECTION_GRADING);
        ScoringBacklog backlog = cache.getOrCompute(key, OverviewConstants.HOST_GRADING_TTL, ScoringBacklog.class,
                () -> scoring.backlog(tenantId));
        return new Grading(backlog.pendingAnswers(), backlog.aiScoringAnswers(), backlog.failedAnswers(),
                backlog.closedSessionsAwaitingFinalisation(), reporting.countAwaitingPublication(tenantId));
    }

    public Subscription subscription(UUID tenantId) {
        SubscriptionSummary summary = billing.subscriptionSummary(tenantId);
        return new Subscription(summary.current(), summary.nearestExpiryAt(), summary.daysToExpiry(),
                summary.expiringSoon());
    }

    public Billing billing(UUID tenantId) {
        return new Billing(billing.pendingOrderCount(tenantId));
    }

    public Support support(UUID tenantId) {
        TicketCounts counts = support.ticketCounts(tenantId);
        return new Support(counts.open(), counts.inProgress(), counts.resolvedAwaitingClose());
    }

    /** Newest audit entries reduced to action, actor, target type and time. */
    public List<ActivityItem> recentActivity(UUID tenantId) {
        return audit.recent(tenantId, OverviewConstants.RECENT_ACTIVITY_LIMIT).stream()
                .map(entry -> new ActivityItem(entry.publicId(), entry.action(), entry.actorUserId(),
                        entry.aggregateType(), entry.createdAt()))
                .toList();
    }
}
