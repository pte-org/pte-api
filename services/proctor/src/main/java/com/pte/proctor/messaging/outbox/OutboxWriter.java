package com.pte.proctor.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.common.messaging.AbstractOutboxWriter;
import com.pte.proctor.domain.OutboxEntry;
import com.pte.proctor.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Writes proctor's outbound commands/events to the outbox in the caller's transaction (ADR-002). */
@Component
public class OutboxWriter extends AbstractOutboxWriter<OutboxEntry> {

    private final OutboxRepository outboxRepository;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        super(objectMapper);
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
