package com.pte.session.internal.service.lifecycle;

import com.pte.billing.SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.SessionCancelledEvent;
import com.pte.session.internal.exception.capacity.SubscriptionRevocationScopeConflictException;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Revocation impact and cancellation events; billing owns the prior subscription lock. */
@Service
public class SessionRevocationService {

    private final ExamSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SessionRevocationService(ExamSessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

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

    @Transactional
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new SubscriptionRevocationScopeConflictException();
        }
        for (ExamSession session : sessionRepository.findBySubscriptionIdAndStatus(
                subscriptionId, SessionStatus.SCHEDULED)) {
            session.cancel();
            eventPublisher.publishEvent(new SessionCancelledEvent(
                    session.getPublicId(), session.getTenantId(), session.getName(), clock.instant()));
        }
        sessionRepository.flush();
    }

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
            eventPublisher.publishEvent(new SessionCancelledEvent(
                    session.getPublicId(), session.getTenantId(), session.getName(), clock.instant()));
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
}
