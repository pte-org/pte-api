package com.pte.notification.internal.service;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** Must join the originating transaction, never make an AFTER_COMMIT/REQUIRES_NEW durability promise. */
@Service
public class InboxAppendService {
    private final InboxDeliveryStore store;
    private final Clock clock;
    public InboxAppendService(InboxDeliveryStore store, Clock clock) { this.store = store; this.clock = clock; }
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(InboxNotificationRequested event) { return store.append(event, clock.instant()); }
}
