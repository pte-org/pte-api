package com.pte.session.internal.listener;

import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.session.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionRevokedSessionListenerTest {

    @Mock
    private SessionService sessionService;

    @Test
    void onSubscriptionRevoked_delegatesToSessionFacade() {
        UUID subscriptionId = UUID.randomUUID();

        new SubscriptionRevokedSessionListener(sessionService)
                .onSubscriptionRevoked(new SubscriptionRevokedEvent(subscriptionId));

        verify(sessionService).cancelScheduledSessionsBySubscription(subscriptionId);
    }
}
