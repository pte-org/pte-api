package com.pte.reporting.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.reporting.domain.OutboxEntry;
import com.pte.reporting.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of reporting's already-published outbox rows (ADR-002 supersession). */
@Component
public class ReportingOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public ReportingOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
