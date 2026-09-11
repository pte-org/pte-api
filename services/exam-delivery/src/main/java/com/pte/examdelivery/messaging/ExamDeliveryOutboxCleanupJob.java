package com.pte.examdelivery.messaging;

import com.pte.common.messaging.AbstractOutboxCleanupJob;
import com.pte.examdelivery.domain.OutboxEntry;
import com.pte.examdelivery.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Daily prune of exam-delivery's already-published outbox rows (ADR-002 supersession). */
@Component
public class ExamDeliveryOutboxCleanupJob extends AbstractOutboxCleanupJob<OutboxEntry> {

    public ExamDeliveryOutboxCleanupJob(OutboxRepository repository) {
        super(repository);
    }
}
