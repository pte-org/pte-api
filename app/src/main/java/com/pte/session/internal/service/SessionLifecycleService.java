package com.pte.session.internal.service;

import com.pte.billing.SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact;
import com.pte.session.domain.ExamSession;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.request.PatchExamScheduleRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import com.pte.session.internal.service.lifecycle.SessionAccess;
import com.pte.session.internal.service.lifecycle.SessionCreationService;
import com.pte.session.internal.service.lifecycle.SessionSubscriptionService;
import com.pte.session.internal.service.lifecycle.SessionStateService;
import com.pte.session.internal.service.lifecycle.SessionPolicyService;
import com.pte.session.internal.service.lifecycle.SessionScheduleService;
import com.pte.session.internal.service.lifecycle.SessionReadService;
import com.pte.session.internal.service.lifecycle.SessionReminderService;
import com.pte.session.internal.service.lifecycle.SessionRevocationService;

/** Compatibility facade. Business responsibilities live in focused lifecycle services. */
@Service
public class SessionLifecycleService {

    private final SessionAccess sessionAccess;
    private final SessionCreationService sessionCreationService;
    private final SessionSubscriptionService sessionSubscriptionService;
    private final SessionStateService sessionStateService;
    private final SessionPolicyService sessionPolicyService;
    private final SessionScheduleService sessionScheduleService;
    private final SessionReadService sessionReadService;
    private final SessionReminderService sessionReminderService;
    private final SessionRevocationService sessionRevocationService;
    private final SessionOperationGuardService sessionOperationGuardService;

    public SessionLifecycleService(SessionAccess sessionAccess,
            SessionCreationService sessionCreationService,
            SessionSubscriptionService sessionSubscriptionService,
            SessionStateService sessionStateService,
            SessionPolicyService sessionPolicyService,
            SessionScheduleService sessionScheduleService,
            SessionReadService sessionReadService,
            SessionReminderService sessionReminderService,
            SessionRevocationService sessionRevocationService,
            SessionOperationGuardService sessionOperationGuardService) {
        this.sessionAccess = sessionAccess;
        this.sessionCreationService = sessionCreationService;
        this.sessionSubscriptionService = sessionSubscriptionService;
        this.sessionStateService = sessionStateService;
        this.sessionPolicyService = sessionPolicyService;
        this.sessionScheduleService = sessionScheduleService;
        this.sessionReadService = sessionReadService;
        this.sessionReminderService = sessionReminderService;
        this.sessionRevocationService = sessionRevocationService;
        this.sessionOperationGuardService = sessionOperationGuardService;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, CurrentUser caller) {
        return sessionCreationService.create(request, caller);
    }

    @Transactional
    public SessionResponse changeSubscription(UUID publicId, ChangeSubscriptionRequest request, CurrentUser caller) {
        return sessionSubscriptionService.changeSubscription(publicId, request, caller);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID publicId, CurrentUser caller) {
        return sessionReadService.get(publicId, caller);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(CurrentUser caller) {
        return sessionReadService.list(caller);
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryView> findSummaries(Collection<UUID> publicIds, UUID tenantId) {
        return sessionReadService.findSummaries(publicIds, tenantId);
    }

    @Transactional(readOnly = true)
    public List<ClosingSoonSessionView> findDueClosingSoonSessions(Instant now, Instant cutoff) {
        return sessionReminderService.findDueClosingSoonSessions(now, cutoff);
    }

    @Transactional
    public Optional<ClosingSoonSessionView> lockDueClosingSoonSession(UUID publicId, Instant now, Instant cutoff) {
        return sessionReminderService.lockDueClosingSoonSession(publicId, now, cutoff);
    }

    @Transactional
    public void lockClosedForGradingCohort(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockClosedForGradingCohort(publicId, tenantId);
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        return sessionStateService.open(publicId, caller);
    }

    @Transactional
    public ExamPolicyResponse patchPolicy(UUID publicId, PatchExamPolicyRequest request, CurrentUser caller) {
        return sessionPolicyService.patchPolicy(publicId, request, caller);
    }

    @Transactional
    public SessionResponse patchSchedule(UUID publicId,
            PatchExamScheduleRequest request,
            CurrentUser caller) {
        return sessionScheduleService.patchSchedule(publicId, request, caller);
    }

    @Transactional
    public SessionResponse close(UUID publicId, CurrentUser caller) {
        return sessionStateService.close(publicId, caller);
    }

    @Transactional
    public SubscriptionRevocationImpact getSubscriptionRevocationImpact(UUID subscriptionId, UUID tenantId) {
        return sessionRevocationService.getSubscriptionRevocationImpact(subscriptionId, tenantId);
    }

    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        sessionRevocationService.cancelScheduledSessionsBySubscription(subscriptionId);
    }

    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId, UUID tenantId) {
        sessionRevocationService.cancelScheduledSessionsBySubscription(subscriptionId, tenantId);
    }

    ExamSession findOwned(UUID publicId, CurrentUser caller) {
        return sessionAccess.findOwned(publicId, caller);
    }

    ExamSession findOwnedWithLock(UUID publicId, CurrentUser caller) {
        return sessionAccess.findOwnedWithLock(publicId, caller);
    }

    @Transactional
    public void lockForExaminerAssignment(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockForExaminerAssignment(publicId, tenantId);
    }

    @Transactional
    public void lockForScoreReviewMutation(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockForScoreReviewMutation(publicId, tenantId);
    }

    @Transactional
    public void lockOpenForAttemptOperation(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockOpenForAttemptOperation(publicId, tenantId);
    }

    @Transactional
    public void lockOpenWithinWindowForProctorOperation(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockOpenWithinWindowForProctorOperation(publicId, tenantId);
    }

    @Transactional(readOnly = true)
    public AttemptRetryPolicyResponse getAttemptRetryPolicy(UUID publicId, UUID tenantId) {
        return sessionReadService.getAttemptRetryPolicy(publicId, tenantId);
    }

    @Transactional
    public void lockClosedForReportPublication(UUID publicId, UUID tenantId) {
        sessionOperationGuardService.lockClosedForReportPublication(publicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isSessionClosed(UUID publicId, UUID tenantId) {
        return sessionReadService.isSessionClosed(publicId, tenantId);
    }

}
