package com.pte.examdelivery.messaging.outbox;

import com.pte.common.messaging.AbstractOutboxWriter;
import com.pte.examdelivery.domain.OutboxEntry;
import com.pte.examdelivery.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes exam-delivery domain events to the outbox in the caller's transaction
 * (ADR-002). This is the ONLY way {@code AnswerSubmitted}/{@code AttemptSubmitted}
 * leave the service — never a direct broker publish inside the business TX.
 */
@Component
public class OutboxWriter extends AbstractOutboxWriter<OutboxEntry> {

    private final OutboxRepository outboxRepository;

    public OutboxWriter(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        super(jsonMapper);
        this.outboxRepository = outboxRepository;
    }

    @Override
    protected OutboxEntry instantiate() {
        return new OutboxEntry();
    }

    @Override
    protected void persist(OutboxEntry entry) {
        outboxRepository.save(entry);
    }
}
