package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ReplayPolicy;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.SessionCancelledEvent;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.session.internal.exception.HostContextRequiredException;
import com.pte.session.internal.exception.InvalidPolicyPatchException;
import com.pte.session.internal.exception.InvalidSessionWindowException;
import com.pte.session.internal.exception.PolicyLockedException;
import com.pte.session.internal.exception.SessionCapacityInvalidException;
import com.pte.session.internal.exception.SessionCapacityRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.session.internal.exception.SessionNotClosedForGradingCohortException;
import com.pte.session.internal.exception.SessionNotClosedForReportPublicationException;
import com.pte.session.internal.exception.SessionNotReadyToOpenException;
import com.pte.session.internal.exception.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.SessionSubscriptionConflictException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.exception.SubscriptionRevocationScopeConflictException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.policy.SessionPolicyResolver;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Session lifecycle: create (gated by an active {@link BillingService}
 * subscription's window/capacity/overlap, then generating a fresh random
 * exam via {@link AssessmentService#generateAndPublish} — an in-process
 * call, no cache/ref table needed now that assessment lives in the same
 * app), open, close. Tenant-scoped throughout — a host operates only on its
 * own tenant's sessions.
 */
@Service
public class SessionLifecycleService {

    private static final String OVERLAP_CONSTRAINT = "no_overlap_per_subscription";

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssessmentService assessmentService;
    private final BillingService billingService;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionCodeGenerator sessionCodeGenerator;

    public SessionLifecycleService(ExamSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            AssessmentService assessmentService,
            BillingService billingService,
            ApplicationEventPublisher eventPublisher,
            SessionCodeGenerator sessionCodeGenerator) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assessmentService = assessmentService;
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
        this.sessionCodeGenerator = sessionCodeGenerator;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        validateWindow(request.opensAt(), request.closesAt());

        SubscriptionView subscription = lockedActiveSubscription(request.subscriptionPublicId(), tenantId);
        if (request.capacity() == null) {
            throw new SessionCapacityRequiredException();
        }
        if (request.capacity() <= 0) {
            throw new SessionCapacityInvalidException();
        }
        validateSubscriptionWindowAndCapacity(subscription, request.opensAt(), request.closesAt(),
                request.capacity(), false);
        rejectOverlap(subscription.publicId(), request.opensAt(), request.closesAt(), null);

        // Generates a fresh random exam from the question bank and publishes it —
        // assessment's own InsufficientQuestionBankException/InvalidSkillSelectionException
        // propagate unmodified if generation fails, before any ExamSession is saved.
        SnapshotResponse snapshot = assessmentService.generateAndPublish(request.name(), request.skills(), caller);

        ExamSession session = new ExamSession();
        session.setName(request.name());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscription.publicId());
        session.setLicenseKey(subscription.licenseKey());
        session.setSnapshotPublicId(snapshot.publicId());
        session.setTemplatePublicId(snapshot.scoreTemplatePublicId());
        session.setTemplateVersion(snapshot.scoreTemplateVersion());
        session.setOpensAt(request.opensAt());
        session.setClosesAt(request.closesAt());
        session.setCapacity(request.capacity());
        ExamMode mode = request.examMode() != null ? request.examMode() : ExamMode.OFFICIAL_EXAM;
        session.setExamMode(mode);
        session.setFormMode(com.pte.session.domain.enums.FormMode.SHARED_FORM);
        session.setReusePolicy(com.pte.session.domain.enums.ReusePolicy.ALLOW);
        ExamPolicy policy = ExamPolicy.realExamDefault();
        policy.setLockdownMode(SessionPolicyResolver.resolveForCreate(request.lockdownMode()));
        session.setPolicy(policy);
        session.setSessionCode(sessionCodeGenerator.generate(tenantId, request.opensAt()));

        try {
            ExamSession saved = sessionRepository.save(session);
            sessionRepository.flush();
            return SessionMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateOverlap(ex);
        }
    }

    @Transactional
    public SessionResponse changeSubscription(UUID publicId, ChangeSubscriptionRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        ExamSession observed = observedSession(publicId, tenantId);
        if (observed.getStatus() != SessionStatus.SCHEDULED) {
            throw new PolicyLockedException();
        }

        UUID oldSubscriptionId = observed.getSubscriptionId();
        UUID targetSubscriptionId = request.subscriptionPublicId();
        if (oldSubscriptionId == null || targetSubscriptionId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<UUID> lockOrder = List.of(oldSubscriptionId, targetSubscriptionId).stream()
                .sorted(Comparator.naturalOrder()).toList();
        List<SubscriptionView> lockedSubscriptions = billingService.lockSubscriptions(lockOrder, tenantId);
        SubscriptionView target = lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(targetSubscriptionId))
                .findFirst()
                .orElseThrow(SessionSubscriptionNotFoundException::new);
        lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(oldSubscriptionId))
                .findFirst()
                .orElseThrow(SessionSubscriptionNotFoundException::new);

        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED
                || !oldSubscriptionId.equals(session.getSubscriptionId())) {
            throw new SessionSubscriptionConflictException();
        }

        long enrollmentCount = enrollmentRepository.countBySessionId(session.getId());
        validateSubscriptionWindowAndCapacity(target, session.getOpensAt(), session.getClosesAt(),
                enrollmentCount, true);
        rejectOverlap(target.publicId(), session.getOpensAt(), session.getClosesAt(), session.getPublicId());

        session.setSubscriptionId(target.publicId());
        session.setLicenseKey(target.licenseKey());
        session.setCapacity(Math.min(session.getCapacity(), target.maxStudentsPerSession()));
        enrollmentRepository.findBySessionId(session.getId())
                .forEach(enrollment -> enrollment.setLicenseKey(target.licenseKey()));

        try {
            sessionRepository.flush();
            return SessionMapper.toResponse(session);
        } catch (DataIntegrityViolationException ex) {
            throw translateOverlap(ex);
        }
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID publicId, CurrentUser caller) {
        return SessionMapper.toResponse(findOwned(publicId, caller));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(CurrentUser caller) {
        return sessionRepository.findByTenantIdOrderByCreatedAtDescIdDesc(requireTenant(caller)).stream()
                .map(SessionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryView> findSummaries(Collection<UUID> publicIds, UUID tenantId) {
        if (publicIds == null || publicIds.isEmpty() || tenantId == null) {
            return List.of();
        }
        return sessionRepository.findByTenantIdAndPublicIdInAndDeletedFalse(tenantId, publicIds.stream().distinct().toList())
                .stream()
                .map(session -> new SessionSummaryView(session.getPublicId(), session.getName(),
                        session.getSessionCode(), session.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClosingSoonSessionView> findDueClosingSoonSessions(Instant now, Instant cutoff) {
        if (now == null || cutoff == null || !cutoff.isAfter(now)) {
            return List.of();
        }
        return sessionRepository.findDueClosingSoon(now, cutoff).stream()
                .map(SessionLifecycleService::toClosingSoonView)
                .toList();
    }

    /** Locks and revalidates the schedule so a stale reminder candidate cannot be appended. */
    @Transactional
    public Optional<ClosingSoonSessionView> lockDueClosingSoonSession(UUID publicId, Instant now, Instant cutoff) {
        if (publicId == null || now == null || cutoff == null || !cutoff.isAfter(now)) {
            return Optional.empty();
        }
        return sessionRepository.findWithLockByPublicId(publicId)
                .filter(session -> !session.isDeleted())
                .filter(session -> session.getStatus() == SessionStatus.OPEN)
                .filter(session -> !session.getOpensAt().isAfter(now))
                .filter(session -> session.getClosesAt().isAfter(now) && !session.getClosesAt().isAfter(cutoff))
                .map(SessionLifecycleService::toClosingSoonView);
    }

    @Transactional
    public void lockClosedForGradingCohort(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionNotClosedForGradingCohortException();
        }
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        ExamSession observed = observedSession(publicId, tenantId);
        if (observed.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotReadyToOpenException();
        }
        UUID subscriptionId = observed.getSubscriptionId();
        SubscriptionView subscription = lockedActiveSubscription(subscriptionId, tenantId);
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotReadyToOpenException();
        }
        if (!java.util.Objects.equals(subscriptionId, session.getSubscriptionId())) {
            throw new SessionSubscriptionConflictException();
        }
        validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(), session.getClosesAt(),
                session.getCapacity(), false);
        session.open();
        return SessionMapper.toResponse(session);
    }

    @Transactional
    public ExamPolicyResponse patchPolicy(UUID publicId, PatchExamPolicyRequest request, CurrentUser caller) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new PolicyLockedException();
        }

        ExamPolicy policy = session.getPolicy();
        if (request.replayPolicyType() != null) {
            if (request.replayPolicyType() == ReplayPolicyType.LIMITED && request.replayPolicyLimit() == null) {
                throw new InvalidPolicyPatchException();
            }
            policy.setReplayPolicy(ReplayPolicy.of(request.replayPolicyType(), request.replayPolicyLimit()));
        }
        if (request.deviceCheckRequired() != null) {
            policy.setDeviceCheckRequired(request.deviceCheckRequired());
        }
        if (request.proctorRequired() != null) {
            policy.setProctorRequired(request.proctorRequired());
        }
        if (request.answerIntegrityLevel() != null) {
            policy.setAnswerIntegrityLevel(request.answerIntegrityLevel());
        }
        if (request.lockdownMode() != null) {
            policy.setLockdownMode(SessionPolicyResolver.resolveForPolicyPatch(
                    policy.getLockdownMode(), request.lockdownMode()));
        }
        return SessionMapper.toPolicy(policy, session.getExamMode(), session.getExamMode() == null);
    }

    @Transactional
    public SessionResponse close(UUID publicId, CurrentUser caller) {
        ExamSession session = findOwnedWithLock(publicId, caller);
        session.close();
        return SessionMapper.toResponse(session);
    }

    /** Returns a locked, tenant-scoped impact snapshot for billing revoke preview. */
    @Transactional
    public SubscriptionRevocationImpact getSubscriptionRevocationImpact(UUID subscriptionId, UUID tenantId) {
        if (subscriptionId == null || tenantId == null) {
            throw new SubscriptionRevocationScopeConflictException();
        }
        List<ExamSession> sessions = lockedSubscriptionSessions(subscriptionId, tenantId);
        List<UUID> scheduled = sessions.stream()
                .filter(session -> session.getStatus() == SessionStatus.SCHEDULED)
                .map(ExamSession::getPublicId)
                .toList();
        int open = (int) sessions.stream().filter(session -> session.getStatus() == SessionStatus.OPEN).count();
        int closed = (int) sessions.stream().filter(session -> session.getStatus() == SessionStatus.CLOSED).count();
        return new SubscriptionRevocationImpact(subscriptionId, tenantId, scheduled,
                scheduled.size(), open, closed);
    }

    /** Cancels only scheduled sessions tied to a revoked subscription. */
    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new SubscriptionRevocationScopeConflictException();
        }
        for (ExamSession session : sessionRepository.findBySubscriptionIdAndStatus(
                subscriptionId, SessionStatus.SCHEDULED)) {
            session.cancel();
            List<UUID> students = enrollmentRepository.findBySessionId(session.getId()).stream()
                    .map(enrollment -> enrollment.getStudentPublicId()).toList();
            eventPublisher.publishEvent(new SessionCancelledEvent(
                    session.getPublicId(), session.getTenantId(), students));
        }
        sessionRepository.flush();
    }

    /** Cancels only tenant-owned scheduled sessions tied to a revoked subscription. */
    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId, UUID tenantId) {
        if (subscriptionId == null || tenantId == null) {
            throw new SubscriptionRevocationScopeConflictException();
        }
        List<ExamSession> sessions = sessionRepository
                .findWithLockBySubscriptionIdOrderByPublicIdAsc(subscriptionId);
        for (ExamSession session : sessions) {
            if (!tenantId.equals(session.getTenantId())) {
                throw new SubscriptionRevocationScopeConflictException();
            }
            if (session.getStatus() != SessionStatus.SCHEDULED) {
                continue;
            }
            session.cancel();
            List<UUID> students = enrollmentRepository.findBySessionId(session.getId()).stream()
                    .map(enrollment -> enrollment.getStudentPublicId()).toList();
            eventPublisher.publishEvent(new SessionCancelledEvent(
                    session.getPublicId(), session.getTenantId(), students));
        }
        sessionRepository.flush();
    }

    private List<ExamSession> lockedSubscriptionSessions(UUID subscriptionId, UUID tenantId) {
        List<ExamSession> sessions = sessionRepository
                .findWithLockBySubscriptionIdOrderByPublicIdAsc(subscriptionId);
        if (sessions.stream().anyMatch(session -> !tenantId.equals(session.getTenantId()))) {
            throw new SubscriptionRevocationScopeConflictException();
        }
        return sessions;
    }

    private ExamSession observedSession(UUID publicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseGet(() -> sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                        .orElseThrow(SessionNotFoundException::new));
    }

    ExamSession findOwned(UUID publicId, CurrentUser caller) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    ExamSession findOwnedWithLock(UUID publicId, CurrentUser caller) {
        return sessionRepository.findWithLockByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    /** Serializes scoring-owned assignment commits with other session-scoped lifecycle changes. */
    @Transactional
    public void lockForExaminerAssignment(UUID publicId, UUID tenantId) {
        if (tenantId == null) {
            throw new HostContextRequiredException();
        }
        sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    @Transactional
    public void lockForScoreReviewMutation(UUID publicId, UUID tenantId) {
        if (tenantId == null) {
            throw new HostContextRequiredException();
        }
        sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    @Transactional
    public void lockOpenForAttemptOperation(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(NotEntitledException::new);
        SessionEntryGate.requireOpen(session);
    }

    @Transactional(readOnly = true)
    public AttemptRetryPolicyResponse getAttemptRetryPolicy(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(NotEntitledException::new);
        return new AttemptRetryPolicyResponse(session.getMaxRetriesPerStudent());
    }

    @Transactional
    public void lockClosedForReportPublication(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionNotClosedForReportPublicationException();
        }
    }

    @Transactional(readOnly = true)
    public boolean isSessionClosed(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        return session.getStatus() == SessionStatus.CLOSED;
    }

    private SubscriptionView lockedActiveSubscription(UUID subscriptionId, UUID tenantId) {
        if (billingService == null || subscriptionId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<SubscriptionView> locked = billingService.lockSubscriptions(List.of(subscriptionId), tenantId);
        if (!locked.isEmpty()) {
            return locked.get(0);
        }
        return billingService.getActiveSubscription(subscriptionId, tenantId)
                .orElseThrow(SessionSubscriptionNotFoundException::new);
    }

    private void validateSubscriptionWindowAndCapacity(SubscriptionView subscription, Instant opensAt,
            Instant closesAt, long capacityOrEnrollmentCount, boolean enrollmentCount) {
        Instant now = Instant.now();
        if (subscription == null || subscription.status() == null || subscription.maxStudentsPerSession() == null
                || subscription.maxStudentsPerSession() <= 0 || !subscription.isUsableAt(now)) {
            throw new SessionSubscriptionNotFoundException();
        }
        if (opensAt.isBefore(subscription.startsAt()) || closesAt.isAfter(subscription.expiresAt())) {
            throw new SessionWindowOutsideSubscriptionException();
        }
        if (enrollmentCount) {
            if (capacityOrEnrollmentCount > subscription.maxStudentsPerSession()) {
                throw SessionSubscriptionCapacityException.forEnrollments(
                        capacityOrEnrollmentCount, subscription.maxStudentsPerSession());
            }
        } else if (capacityOrEnrollmentCount > subscription.maxStudentsPerSession()) {
            throw new SessionSubscriptionCapacityException((int) capacityOrEnrollmentCount,
                    subscription.maxStudentsPerSession());
        }
    }

    private void validateWindow(Instant opensAt, Instant closesAt) {
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new InvalidSessionWindowException();
        }
    }

    private void rejectOverlap(UUID subscriptionId, Instant opensAt, Instant closesAt, UUID excludedPublicId) {
        sessionRepository.findFirstOverlapping(subscriptionId, opensAt, closesAt, excludedPublicId)
                .ifPresent(conflict -> {
                    throw new SessionTimeConflictException(conflict.getPublicId());
                });
    }

    private RuntimeException translateOverlap(DataIntegrityViolationException ex) {
        if (containsConstraint(ex, OVERLAP_CONSTRAINT)) {
            return new SessionTimeConflictException();
        }
        return ex;
    }

    private boolean containsConstraint(Throwable throwable, String constraint) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private UUID requireTenant(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) {
            throw new HostContextRequiredException();
        }
        return caller.tenantId();
    }

    private static ClosingSoonSessionView toClosingSoonView(ExamSession session) {
        return new ClosingSoonSessionView(session.getPublicId(), session.getTenantId(), session.getName(),
                session.getOpensAt(), session.getClosesAt());
    }
}
