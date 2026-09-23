package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ReplayPolicy;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.SessionCancelledEvent;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.HostContextRequiredException;
import com.pte.session.internal.exception.InvalidPolicyPatchException;
import com.pte.session.internal.exception.InvalidSessionWindowException;
import com.pte.session.internal.exception.PolicyLockedException;
import com.pte.session.internal.exception.SessionCapacityInvalidException;
import com.pte.session.internal.exception.SessionCapacityRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.SessionNotReadyToOpenException;
import com.pte.session.internal.exception.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.mapper.SessionMapper;
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

    public SessionLifecycleService(ExamSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            AssessmentService assessmentService,
            BillingService billingService,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assessmentService = assessmentService;
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        validateWindow(request.opensAt(), request.closesAt());

        SubscriptionView subscription = activeSubscription(request.subscriptionPublicId(), tenantId);
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
        ExamMode mode = request.examMode() != null ? request.examMode() : ExamMode.MOCK_TEST;
        session.setExamMode(mode);
        session.setFormMode(com.pte.session.domain.enums.FormMode.SHARED_FORM);
        session.setReusePolicy(com.pte.session.domain.enums.ReusePolicy.ALLOW);
        ExamPolicy policy = ExamPolicy.forMode(mode);

        if (request.lockdownMode() != null) {
            if (mode == ExamMode.PRACTICE && request.lockdownMode() == LockdownMode.STRICT) {
                throw new IllegalArgumentException(SessionConstants.STRICT_LOCKDOWN_NOT_ALLOWED_FOR_PRACTICE);
            }
            policy.setLockdownMode(request.lockdownMode());
        }
        session.setPolicy(policy);

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
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new PolicyLockedException();
        }

        List<UUID> lockOrder = List.of(session.getSubscriptionId(), request.subscriptionPublicId()).stream()
                .sorted(Comparator.naturalOrder()).toList();
        List<SubscriptionView> lockedSubscriptions = billingService.lockSubscriptions(lockOrder, tenantId);
        SubscriptionView target = lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(request.subscriptionPublicId()))
                .findFirst()
                .orElseThrow(SessionSubscriptionNotFoundException::new);

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
        return sessionRepository.findByTenantId(requireTenant(caller)).stream()
                .map(SessionMapper::toResponse).toList();
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotReadyToOpenException();
        }
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
            policy.setLockdownMode(request.lockdownMode());
        }
        return SessionMapper.toPolicy(policy);
    }

    @Transactional
    public SessionResponse close(UUID publicId, CurrentUser caller) {
        ExamSession session = findOwned(publicId, caller);
        session.close();
        return SessionMapper.toResponse(session);
    }

    /** Cancels only scheduled sessions tied to a revoked subscription. */
    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        for (ExamSession session : sessionRepository.findBySubscriptionIdAndStatus(
                subscriptionId, SessionStatus.SCHEDULED)) {
            session.cancel();
            List<UUID> students = enrollmentRepository.findBySessionId(session.getId()).stream()
                    .map(enrollment -> enrollment.getStudentPublicId()).toList();
            eventPublisher.publishEvent(new SessionCancelledEvent(session.getPublicId(), session.getTenantId(), students));
        }
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

    private SubscriptionView activeSubscription(UUID subscriptionId, UUID tenantId) {
        if (billingService == null) {
            throw new IllegalStateException("BillingService is required for session creation");
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
}
