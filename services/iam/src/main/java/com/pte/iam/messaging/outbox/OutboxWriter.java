package com.pte.iam.messaging.outbox;

import com.pte.common.messaging.AbstractOutboxWriter;
import com.pte.iam.domain.OutboxEntry;
import com.pte.iam.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes iam domain events to the outbox in the caller's transaction (ADR-002). */
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
