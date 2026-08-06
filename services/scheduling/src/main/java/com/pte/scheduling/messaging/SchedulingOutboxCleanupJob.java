package com.pte.scheduling.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.scheduling.domain.OutboxEntry;
import com.pte.scheduling.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of scheduling's already-published outbox rows (ADR-002 supersession). */
@Component
public class SchedulingOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public SchedulingOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
