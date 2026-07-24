package com.pte.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared outbox-writing logic (serialize payload + fill row). A service subclass
 * supplies its concrete entity + how to persist it. Callers must invoke
 * {@link #write} INSIDE the same {@code @Transactional} as the business change so
 * state and event are atomic (ADR-002).
 *
 * @param <T> the service's concrete outbox entity
 */
public abstract class AbstractOutboxWriter<T extends AbstractOutboxEntry> {

    private final ObjectMapper objectMapper;

    protected AbstractOutboxWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Create a new instance of the concrete outbox entity. */
    protected abstract T instantiate();

    /** Persist the entity via the service's repository. */
    protected abstract void persist(T entry);

    public void write(String aggregateType, String aggregateId, String eventType, Object payload, UUID tenantId) {
        try {
            T entry = instantiate();
            entry.setAggregateType(aggregateType);
            entry.setAggregateId(aggregateId);
            entry.setEventType(eventType);
            entry.setPayload(objectMapper.writeValueAsString(payload));
            entry.setTenantId(tenantId);
            entry.setOccurredAt(Instant.now());
            persist(entry);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, ex);
        }
    }
}
