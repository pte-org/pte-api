package com.pte.admin.messaging;

import com.pte.admin.domain.OutboxEntry;
import com.pte.admin.repository.OutboxRepository;
import com.pte.common.messaging.AbstractOutboxCleanupJob;
import org.springframework.stereotype.Component;

/** Daily prune of admin's already-published outbox rows (ADR-002 supersession). */
@Component
public class AdminOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public AdminOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
