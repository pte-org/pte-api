package com.pte.reporting.messaging.outbox;

import com.pte.common.messaging.AbstractOutboxWriter;
import com.pte.reporting.domain.OutboxEntry;
import com.pte.reporting.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes reporting domain events to the outbox in the caller's transaction (ADR-002). */
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
