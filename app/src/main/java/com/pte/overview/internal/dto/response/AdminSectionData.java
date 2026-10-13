package com.pte.overview.internal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload records of the platform admin dashboard sections. Every record is plain data so a
 * section can be cached as JSON; none carries a secret (no license code, contact detail,
 * ticket text or payment data).
 */
public final class AdminSectionData {

    private AdminSectionData() {
    }

    /**
     * Tenant counts are current values (status has no history, so no trend). {@code byOrganizationType}
     * is keyed by the stored type. {@code monthlyCreated} is oldest first and ends with the
     * current (partial) month in Asia/Ho_Chi_Minh.
     */
    public record Tenants(long total, long active, long suspended, Map<String, Long> byOrganizationType,
            TrendValue newInWindow, List<MonthlyPoint> monthlyCreated) {
    }

    /**
     * {@code totalStudents} and {@code totalSeats} are current values (seat usage has no history);
     * {@code activeLearners} counts distinct students who started an attempt in the window, platform-wide.
     * {@code topUtilisation} lists the fullest tenants by students vs seat limit.
     */
    public record Learners(long totalStudents, long totalSeats, TrendValue activeLearners,
            List<SeatUtilisation> topUtilisation) {
    }

    /** {@code utilisationPct} is students / seatLimit * 100, computed by the backend (one decimal). */
    public record SeatUtilisation(UUID tenantId, String code, String name, long students, long seatLimit,
            double utilisationPct) {
    }

    /** Newest tenants first (a wrapper, so the section can be cached and read back by type). */
    public record RecentTenants(List<RecentTenant> items) {
    }

    /**
     * One recent tenant. {@code planName}/{@code subscriptionExpiresAt} come from the tenant's
     * soonest-expiring usable subscription and are null when it has none.
     */
    public record RecentTenant(UUID tenantId, String code, String name, String organizationType, String status,
            String planName, long studentCount, long seatLimit, Instant subscriptionExpiresAt, Instant createdAt) {
    }

    /** {@code oldestPending} is oldest first (longest waiting). */
    public record Applications(long pending, TrendValue receivedInWindow, List<PendingApplication> oldestPending) {
    }

    public record PendingApplication(UUID applicationId, String orgName, String orgType, String requestedCode,
            Instant receivedAt) {
    }

    /**
     * Counts by derived state (an issued code past its expiry is {@code expired}); no code value is
     * ever returned. {@code expiringSoon} covers issued codes ending within {@code expiringSoonDays}.
     */
    public record Licenses(long issuedValid, long redeemed, long revoked, long expired, long expiringSoon,
            int expiringSoonDays, TrendValue redeemedInWindow) {
    }

    /**
     * {@code active} counts usable subscriptions now; {@code expiringSoon} those ending within
     * {@code expiringSoonDays}. {@code activatedBySource} splits the subscriptions activated in the
     * current window by activation source (every source present).
     */
    public record Subscriptions(long active, long expiringSoon, int expiringSoonDays, TrendValue activatedInWindow,
            Map<String, Long> activatedBySource) {
    }

    public record Plans(long active, long draft, long archived, Map<String, Long> byType) {
    }

    /**
     * Platform-wide ticket counts. {@code oldestOpen} lists tickets still in the OPEN state, oldest first,
     * with safe fields only (no description, submitter or notes).
     */
    public record Support(long open, long inProgress, TrendValue openedInWindow, List<OldestTicket> oldestOpen) {
    }

    /** {@code tenantName} is null when the tenant can no longer be resolved. */
    public record OldestTicket(UUID ticketId, UUID tenantId, String tenantName, String category, Instant openedAt) {
    }
}
