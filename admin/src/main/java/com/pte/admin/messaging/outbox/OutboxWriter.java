package com.pte.admin.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.admin.domain.OutboxEntry;
import com.pte.admin.repository.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Persists a domain event as an outbox row inside the caller's transaction
 * (ADR-002). Debezium relays these to Kafka in Phase 6.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void write(String aggregateType, String aggregateId, String eventType, Object payload, UUID tenantId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEntry.of(aggregateType, aggregateId, eventType, json, tenantId));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, ex);
        }
    }
}
