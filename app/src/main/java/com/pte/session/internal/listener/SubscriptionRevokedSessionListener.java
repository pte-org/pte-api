package com.pte.session.internal.listener;

import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.session.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/** Joins billing revocation before commit so session cancellation is atomic. */
@Component
public class SubscriptionRevokedSessionListener {

    private final SessionService sessionService;

    public SubscriptionRevokedSessionListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSubscriptionRevoked(SubscriptionRevokedEvent event) {
        if (event.tenantPublicId() == null) {
            sessionService.cancelScheduledSessionsBySubscription(event.subscriptionPublicId());
            return;
        }
        sessionService.cancelScheduledSessionsBySubscription(
                event.subscriptionPublicId(), event.tenantPublicId());
    }
}
