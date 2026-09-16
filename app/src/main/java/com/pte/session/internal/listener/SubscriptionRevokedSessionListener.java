package com.pte.session.internal.listener;

import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.session.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Bridges billing revocation to the session public facade after billing commits. */
@Component
public class SubscriptionRevokedSessionListener {

    private final SessionService sessionService;

    public SubscriptionRevokedSessionListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @TransactionalEventListener
    public void onSubscriptionRevoked(SubscriptionRevokedEvent event) {
        sessionService.cancelScheduledSessionsBySubscription(event.subscriptionPublicId());
    }
}
