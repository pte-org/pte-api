package com.pte.session.internal.listener;

import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.session.SessionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Answers billing's synchronous, tenant-scoped subscription impact query. */
@Component
public class SubscriptionRevocationImpactListener {

    private final SessionService sessionService;

    public SubscriptionRevocationImpactListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @EventListener
    public void onImpactQuery(SubscriptionRevocationImpactQuery query) {
        query.respond(sessionService.getSubscriptionRevocationImpact(
                query.subscriptionPublicId(), query.tenantPublicId()));
    }
}
