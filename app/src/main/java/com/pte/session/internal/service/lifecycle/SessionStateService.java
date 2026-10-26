package com.pte.session.internal.service.lifecycle;

import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.lifecycle.SessionNotOpenToCloseException;
import com.pte.session.internal.exception.lifecycle.SessionNotReadyToOpenException;
import com.pte.session.internal.exception.capacity.SessionSubscriptionConflictException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Session state transitions; domain entities retain state-machine validation. */
@Service
public class SessionStateService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;
    private final SessionSubscriptionRules subscriptions;
    private final AuditLogService auditLogService;
    private final TenancyService tenancyService;

    public SessionStateService(ExamSessionRepository sessionRepository,
            SessionAccess access,
            SessionSubscriptionRules subscriptions,
            AuditLogService auditLogService) {
        this(sessionRepository, access, subscriptions, auditLogService, null);
    }

    @Autowired
    public SessionStateService(ExamSessionRepository sessionRepository,
            SessionAccess access,
            SessionSubscriptionRules subscriptions,
            AuditLogService auditLogService,
            TenancyService tenancyService) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.subscriptions = subscriptions;
        this.auditLogService = auditLogService;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        assertTenantActive(tenantId);
        ExamSession observed = access.observedSession(publicId, tenantId);
        if (observed.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotReadyToOpenException();
        }
        UUID subscriptionId = observed.getSubscriptionId();
        SubscriptionView subscription = subscriptions.lockedActiveSubscription(subscriptionId, tenantId);
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotReadyToOpenException();
        }
        if (!java.util.Objects.equals(subscriptionId, session.getSubscriptionId())) {
            throw new SessionSubscriptionConflictException();
        }
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(), session.getClosesAt(),
                session.getCapacity(), false);
        session.open();
        return SessionMapper.toResponse(session);
    }

    @Transactional
    public SessionResponse close(UUID publicId, CurrentUser caller) {
        ExamSession session = access.findOwnedWithLock(publicId, caller);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new SessionNotOpenToCloseException();
        }
        return closeOpenSession(session, caller, SessionConstants.EVENT_EXAM_CLOSED);
    }

    /**
     * Revalidates a candidate under a row lock so a scheduled auto-close cannot
     * race with a manual close or a cancellation.
     */
    @Transactional
    public Optional<SessionResponse> closeIfDue(UUID publicId, Instant now) {
        ExamSession session = sessionRepository.findWithLockByPublicId(publicId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.OPEN
                || session.getClosesAt() == null || session.getClosesAt().isAfter(now)) {
            return Optional.empty();
        }
        CurrentUser systemCaller = new CurrentUser(SessionConstants.SYSTEM_ACTOR_USER_ID,
                session.getTenantId(), List.of("SYSTEM"));
        return Optional.of(closeOpenSession(session, systemCaller, SessionConstants.EVENT_EXAM_AUTO_CLOSED));
    }

    /**
     * Revalidates a scheduled candidate under a row lock so the persisted
     * schedule, subscription entitlement, and manual open command cannot race.
     */
    @Transactional
    public Optional<SessionResponse> openIfDue(UUID publicId, Instant now) {
        ExamSession session = sessionRepository.findWithLockByPublicId(publicId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.SCHEDULED
                || session.getOpensAt() == null || session.getOpensAt().isAfter(now)
                || session.getClosesAt() == null) {
            return Optional.empty();
        }
        assertTenantActive(session.getTenantId());
        CurrentUser systemCaller = new CurrentUser(SessionConstants.SYSTEM_ACTOR_USER_ID,
                session.getTenantId(), List.of("SYSTEM"));
        SubscriptionView subscription = subscriptions.lockedActiveSubscription(
                session.getSubscriptionId(), session.getTenantId());
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(),
                session.getClosesAt(), session.getCapacity(), false);
        session.open();
        recordExamAudit(systemCaller, session, SessionConstants.EVENT_EXAM_AUTO_OPENED,
                "%s (%s -> %s)".formatted(session.getName(), SessionStatus.SCHEDULED, SessionStatus.OPEN));
        if (!session.getClosesAt().isAfter(now)) {
            session.close();
            recordExamAudit(systemCaller, session, SessionConstants.EVENT_EXAM_AUTO_CLOSED,
                    "%s (%s -> %s)".formatted(session.getName(), SessionStatus.OPEN, SessionStatus.CLOSED));
        }
        return Optional.of(SessionMapper.toResponse(session));
    }

    private SessionResponse closeOpenSession(ExamSession session, CurrentUser caller, String auditEvent) {
        session.close();
        recordExamAudit(caller, session, auditEvent,
                "%s (%s -> %s)".formatted(session.getName(), SessionStatus.OPEN, SessionStatus.CLOSED));
        return SessionMapper.toResponse(session);
    }

    private void recordExamAudit(CurrentUser caller, ExamSession session, String action, String summary) {
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(), action, summary);
    }

    private void assertTenantActive(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }
}
