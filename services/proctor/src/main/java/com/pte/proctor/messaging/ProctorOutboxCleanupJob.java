package com.pte.proctor.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.proctor.domain.OutboxEntry;
import com.pte.proctor.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of proctor's already-published outbox rows (ADR-002 supersession). */
@Component
public class ProctorOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public ProctorOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
