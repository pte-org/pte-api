package com.pte.session;

import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.billing.SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact;
import com.pte.session.dto.response.MonitoringEnrollmentView;
import com.pte.session.dto.response.ProctorAssignedSessionsPageView;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.dto.response.ProctorWorkspaceOverviewView;
import com.pte.session.internal.service.EntitlementService;
import com.pte.session.internal.service.ProctorMonitoringQueryService;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.SessionStaffAssignmentQueryService;
import com.pte.shared.web.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

/**
 * The only door other modules use to reach {@code session}. {@code
 * SessionLifecycleService}, repositories, and controllers stay in {@code
 * internal/}.
 *
 * <p>Trusted-caller checks known to have cross-module callers: {@code
 * attempt} (Phase 07) verifying a student's standing before pinning an
 * attempt, {@code proctoring} (Phase 09) verifying a proctor's assignment
 * before opening a proctor session, and {@code scoring} (Phase 08) verifying
 * a host owns a session before triggering its scoring command. Session CRUD,
 * composition, and enrollment stay internal — only a host's own scheduling UI
 * calls those, through the respective controllers directly.
 */
@Service
public class SessionService {

    private final EntitlementService entitlementService;
    private final SessionLifecycleService sessionLifecycleService;
    private final SessionStaffAssignmentQueryService staffAssignmentQueryService;
    private final ProctorMonitoringQueryService proctorMonitoringQueryService;
    private final com.pte.session.internal.service.AttendanceQueryService attendanceQueryService;
    private final com.pte.session.internal.service.AttendanceCommandService attendanceCommandService;

    public SessionService(
            EntitlementService entitlementService,
            SessionLifecycleService sessionLifecycleService,
            SessionStaffAssignmentQueryService staffAssignmentQueryService,
            ProctorMonitoringQueryService proctorMonitoringQueryService,
            com.pte.session.internal.service.AttendanceQueryService attendanceQueryService,
            com.pte.session.internal.service.AttendanceCommandService attendanceCommandService) {
        this.entitlementService = entitlementService;
        this.sessionLifecycleService = sessionLifecycleService;
        this.staffAssignmentQueryService = staffAssignmentQueryService;
        this.proctorMonitoringQueryService = proctorMonitoringQueryService;
        this.attendanceQueryService = attendanceQueryService;
        this.attendanceCommandService = attendanceCommandService;
    }

    /** Throws if the student isn't enrolled, or the session isn't OPEN or is outside its opensAt/closesAt window — never returns a false/empty result for "not entitled". */
    public EntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        return entitlementService.checkEntitlement(sessionPublicId, studentPublicId);
    }

    /** Throws if the proctor isn't assigned to the session. */
    public ProctorAssignmentCheckResponse checkProctorAssignment(UUID sessionPublicId, UUID proctorPublicId) {
        return entitlementService.checkProctorAssignment(sessionPublicId, proctorPublicId);
    }

    /** Resolves the tenant owning a session for trusted platform-level operations. */
    public UUID resolveTenantId(UUID sessionPublicId) {
        return entitlementService.resolveTenantId(sessionPublicId);
    }

    /** Throws if the session doesn't exist in the caller's tenant. */
    public void verifyHostAccess(UUID sessionPublicId, UUID tenantId) {
        entitlementService.verifyHostAccess(sessionPublicId, tenantId);
    }

    /** Exam code to session publicId for an enrolled student of the tenant; one 404 for every lookup failure. */
    public UUID resolveSessionCode(String code, UUID tenantId, UUID studentPublicId) {
        return entitlementService.resolveSessionCode(code, tenantId, studentPublicId);
    }

    /** Locks an owned session row for the duration of a caller's assignment transaction. */
    @Transactional
    public void lockForExaminerAssignment(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockForExaminerAssignment(sessionPublicId, tenantId);
    }

    /** Serializes Host source selection with report publication for a session. */
    @Transactional
    public void lockForScoreReviewMutation(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockForScoreReviewMutation(sessionPublicId, tenantId);
    }

    /** Locks the session row and rejects student task operations after the cutoff. */
    @Transactional
    public void lockOpenForAttemptOperation(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockOpenForAttemptOperation(sessionPublicId, tenantId);
    }

    /** New-attempt gate; resuming an existing attempt uses the operation lock only. */
    @Transactional(readOnly = true)
    public void assertTenantActiveForNewAttempt(UUID tenantId) {
        sessionLifecycleService.assertTenantActiveForNewAttempt(tenantId);
    }

    /** Locks the session row and asserts session is OPEN and within active exam window for proctoring operations. */
    @Transactional
    public void lockOpenWithinWindowForProctorOperation(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
    }

    /** Reads the retry policy through the session module after its row has been locked for this attempt operation. */
    public AttemptRetryPolicyResponse getAttemptRetryPolicy(UUID sessionPublicId, UUID tenantId) {
        return sessionLifecycleService.getAttemptRetryPolicy(sessionPublicId, tenantId);
    }

    /** Holds the session row lock while Reporting validates CLOSED and publishes the cohort. */
    @Transactional
    public void lockClosedForReportPublication(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockClosedForReportPublication(sessionPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isSessionClosed(UUID sessionPublicId, UUID tenantId) {
        return sessionLifecycleService.isSessionClosed(sessionPublicId, tenantId);
    }

    /** Trusted notification read surface; the lifecycle service revalidates before append. */
    public List<ClosingSoonSessionView> findDueClosingSoonSessions(Instant now, Instant cutoff) {
        return sessionLifecycleService.findDueClosingSoonSessions(now, cutoff);
    }

    /** Reads tenant-owned session labels in one batch for activity projections. */
    @Transactional(readOnly = true)
    public List<SessionSummaryView> findSummaries(Collection<UUID> publicIds, UUID tenantId) {
        return sessionLifecycleService.findSummaries(publicIds, tenantId);
    }

    /** Locks and revalidates one candidate inside the caller's transaction. */
    @Transactional
    public Optional<ClosingSoonSessionView> lockDueClosingSoonSession(UUID sessionPublicId, Instant now,
            Instant cutoff) {
        return sessionLifecycleService.lockDueClosingSoonSession(sessionPublicId, now, cutoff);
    }

    /** Holds the session lock while scoring freezes a post-CLOSED grading cohort. */
    @Transactional
    public void lockClosedForGradingCohort(UUID sessionPublicId, UUID tenantId) {
        sessionLifecycleService.lockClosedForGradingCohort(sessionPublicId, tenantId);
    }

    /** Cancels scheduled sessions for a revoked subscription; open/closed sessions are untouched. */
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        sessionLifecycleService.cancelScheduledSessionsBySubscription(subscriptionId);
    }

    /** Cancels only tenant-owned scheduled sessions inside the billing transaction. */
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId, UUID tenantId) {
        sessionLifecycleService.cancelScheduledSessionsBySubscription(subscriptionId, tenantId);
    }

    /** Returns a locked, tenant-scoped impact snapshot for billing revoke preview. */
    @Transactional
    public SubscriptionRevocationImpact getSubscriptionRevocationImpact(
            UUID subscriptionId, UUID tenantId) {
        return sessionLifecycleService.getSubscriptionRevocationImpact(subscriptionId, tenantId);
    }

    /** Reads session metadata and assignment role for an assigned proctor. */
    public Optional<ProctorMonitoringSessionView> getMonitoringSession(
            UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        return proctorMonitoringQueryService.getMonitoringSession(sessionPublicId, proctorPublicId, tenantId);
    }

    /** Returns overview summary and priority sessions for proctor workspace. */
    public ProctorWorkspaceOverviewView getProctorWorkspaceOverview(
            UUID proctorPublicId, UUID tenantId, String timezone) {
        return proctorMonitoringQueryService.getWorkspaceOverview(proctorPublicId, tenantId, timezone);
    }

    /** Returns paginated assigned sessions for the proctor within tenant with scope, timezone and sort. */
    public ProctorAssignedSessionsPageView getAssignedSessionsForProctor(
            UUID proctorPublicId, UUID tenantId, String scope, String timezone, String sort, int page, int size) {
        return proctorMonitoringQueryService.getAssignedSessions(proctorPublicId, tenantId, scope, timezone, sort, page, size);
    }

    /** Returns paginated assigned sessions for the proctor within tenant. */
    public PagedResult<ProctorMonitoringSessionView> getAssignedSessionsForProctor(
            UUID proctorPublicId, UUID tenantId, int page, int size) {
        return proctorMonitoringQueryService.getAssignedSessions(proctorPublicId, tenantId, page, size);
    }

    /** Returns paginated student enrollments for an exam session within tenant. */
    public PagedResult<MonitoringEnrollmentView> getMonitoringEnrollments(
            UUID sessionPublicId, UUID tenantId, int page, int size) {
        return proctorMonitoringQueryService.getEnrollments(sessionPublicId, tenantId, page, size);
    }

    /** Total count of active student enrollments for an exam session. */
    public long countMonitoringEnrollments(UUID sessionPublicId, UUID tenantId) {
        return proctorMonitoringQueryService.countEnrollments(sessionPublicId, tenantId);
    }

    /** Checks whether a student is actively enrolled in an exam session. */
    public boolean isStudentEnrolledInSession(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        return proctorMonitoringQueryService.isStudentEnrolled(sessionPublicId, studentPublicId, tenantId);
    }

    /** Checks whether a proctor is actively assigned to an exam session. */
    public boolean isProctorAssignedToSession(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        return proctorMonitoringQueryService.isProctorAssigned(sessionPublicId, proctorPublicId, tenantId);
    }

    /** Returns paginated attendance roster joining active enrollments, attendance status and student search view. */
    public com.pte.session.dto.response.AttendancePageView getAttendanceRoster(
            UUID sessionPublicId, String status, String search, int page, int size,
            com.pte.shared.security.CurrentUser caller, boolean isHost) {
        return attendanceQueryService.getRosterPage(sessionPublicId, caller.tenantId(), status, search, page, size, caller, isHost);
    }

    /** Records or corrects attendance under session lock with idempotent request deduplication. */
    public com.pte.session.dto.response.AttendanceCommandReceiptView recordAttendance(
            UUID sessionPublicId, UUID studentPublicId,
            com.pte.session.dto.request.RecordAttendanceRequest request,
            com.pte.shared.security.CurrentUser caller, boolean isHost) {
        return attendanceCommandService.recordAttendance(sessionPublicId, studentPublicId, request, caller, isHost);
    }

    /** Retrieves an exact attendance command receipt by requestPublicId for the caller. */
    public com.pte.session.dto.response.AttendanceCommandReceiptView getAttendanceReceipt(
            UUID sessionPublicId, UUID requestPublicId,
            com.pte.shared.security.CurrentUser caller, boolean isHost) {
        return attendanceCommandService.getReceipt(sessionPublicId, requestPublicId, caller, isHost);
    }

    /** Returns paginated immutable attendance transition history for a student on an exam session. */
    public com.pte.session.dto.response.AttendanceHistoryView getAttendanceHistory(
            UUID sessionPublicId, UUID studentPublicId, int page, int size,
            com.pte.shared.security.CurrentUser caller, boolean isHost) {
        return attendanceCommandService.getHistory(sessionPublicId, studentPublicId, page, size, caller, isHost);
    }

    /** Records or updates student identity verification. */
    public com.pte.session.dto.response.AttendanceRowView recordIdentityVerification(
            UUID sessionPublicId, UUID studentPublicId,
            com.pte.session.dto.request.RecordIdentityVerificationRequest request,
            com.pte.shared.security.CurrentUser caller, boolean isHost) {
        return attendanceCommandService.recordIdentityVerification(sessionPublicId, studentPublicId, request, caller, isHost);
    }

    /** Returns active assignment rows; identity validates the user's live staff role before dispatch. */
    @Transactional(readOnly = true)
    public List<UUID> findAssignedProctorPublicIds(UUID sessionPublicId, UUID tenantId) {
        return staffAssignmentQueryService.findAssignedProctorPublicIds(sessionPublicId, tenantId);
    }
}
