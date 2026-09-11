package com.pte.common.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared outbox-writing logic (serialize payload + fill row). A service subclass
 * supplies its concrete entity + how to persist it. Callers must invoke
 * {@link #write} INSIDE the same {@code @Transactional} as the business change so
 * state and event are atomic (ADR-002).
 *
 * <p>Uses the fleet's shared {@link JsonMapper} bean (Jackson 3, see
 * {@link PteJacksonConfig}), which pins {@code Instant} (de)serialization to ISO-8601 —
 * see {@link PteJacksonConfig} for why. {@link JsonMapper#writeValueAsString} throws the
 * unchecked {@link JacksonException} (Jackson 3), not Jackson 2's checked
 * {@code JsonProcessingException}; still caught explicitly below and wrapped so callers'
 * observable behavior on a serialization failure is unchanged from before this migration.
 *
 * @param <T> the service's concrete outbox entity
 */
public abstract class AbstractOutboxWriter<T extends AbstractOutboxEntry> {

    private final JsonMapper jsonMapper;

    protected AbstractOutboxWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
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
            entry.setPayload(jsonMapper.writeValueAsString(payload));
            entry.setTenantId(tenantId);
            entry.setOccurredAt(Instant.now());
            persist(entry);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, ex);
        }
    }
}
