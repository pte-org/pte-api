package com.pte.session.internal.service.lifecycle;

import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.SessionNotReadyToOpenException;
import com.pte.session.internal.exception.SessionSubscriptionConflictException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Session state transitions; domain entities retain state-machine validation. */
@Service
public class SessionStateService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;
    private final SessionSubscriptionRules subscriptions;

    public SessionStateService(ExamSessionRepository sessionRepository,
            SessionAccess access,
            SessionSubscriptionRules subscriptions) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
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
        session.close();
        return SessionMapper.toResponse(session);
    }
}
