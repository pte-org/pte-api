package com.pte.reporting.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.common.messaging.AbstractOutboxWriter;
import com.pte.reporting.domain.OutboxEntry;
import com.pte.reporting.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Writes reporting domain events to the outbox in the caller's transaction (ADR-002). */
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
