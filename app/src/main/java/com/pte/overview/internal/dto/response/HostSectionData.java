package com.pte.overview.internal.dto.response;

import com.pte.billing.CurrentSubscriptionView;
import com.pte.session.UpcomingSessionView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payload records of the host dashboard sections. */
public final class HostSectionData {

    private HostSectionData() {
    }

    /**
     * {@code total}/{@code active}/{@code suspended} are current student counts;
     * {@code activeLearners} counts distinct students who started an attempt in the window.
     */
    public record Learners(long total, long active, long suspended, TrendValue newInWindow,
            TrendValue activeLearners, long unassigned) {
    }

    public record Quota(long used, long limit, long remaining) {
    }

    public record Academics(long organizations, long programs, long activeClasses) {
    }

    /** Active, non-deleted exam staff. */
    public record Staff(long proctors, long examiners) {
    }

    /** {@code byStatus} holds every session status (0 when none); {@code upcoming} is a short list. */
    public record Exams(Map<String, Long> byStatus, TrendValue created, List<UpcomingSessionView> upcoming) {
    }

    /** {@code monthlySubmitted} is oldest first and ends with the current (partial) month. */
    public record Attempts(TrendValue submitted, long inProgressNow, List<MonthlyPoint> monthlySubmitted) {
    }

    /**
     * Grading backlog (point-in-time counts). {@code closedSessionsAwaitingFinalisation} counts
     * sessions whose grading cohort is frozen but not yet completed.
     */
    public record Grading(long pendingAnswers, long aiScoringAnswers, long failedAnswers,
            long closedSessionsAwaitingFinalisation, long reportsAwaitingPublication) {
    }

    /**
     * Usable subscriptions soonest expiry first. {@code daysToExpiry} is whole days from now to
     * {@code nearestExpiryAt} (null when there is no subscription).
     */
    public record Subscription(List<CurrentSubscriptionView> current, Instant nearestExpiryAt, Long daysToExpiry,
            boolean expiringSoon) {
    }

    /** Orders awaiting payment, as a count only: no payment link or provider data is ever returned. */
    public record Billing(long pendingOrders) {
    }

    /** The tenant's own support tickets by workflow state. */
    public record Support(long open, long inProgress, long resolvedAwaitingClose) {
    }

    /** One audit entry reduced to safe, display-ready fields (no summary text, target id or IP). */
    public record ActivityItem(UUID id, String action, UUID actorUserId, String targetType, Instant occurredAt) {
    }
}
