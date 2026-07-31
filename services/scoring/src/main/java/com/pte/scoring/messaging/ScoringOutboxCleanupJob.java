package com.pte.scoring.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.scoring.domain.OutboxEntry;
import com.pte.scoring.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of scoring's already-published outbox rows (ADR-002 supersession). */
@Component
public class ScoringOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public ScoringOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
