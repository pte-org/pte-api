package com.pte.authoring.messaging;

import com.pte.authoring.domain.OutboxEntry;
import com.pte.authoring.repository.OutboxRepository;
import com.pte.common.messaging.AbstractOutboxCleanupJob;
import org.springframework.stereotype.Component;

/** Daily prune of authoring's already-published outbox rows (ADR-002 supersession). */
@Component
public class AuthoringOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public AuthoringOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
