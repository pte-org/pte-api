package com.pte.iam.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.iam.domain.OutboxEntry;
import com.pte.iam.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of iam's already-published outbox rows (ADR-002 supersession). */
@Component
public class IamOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public IamOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
