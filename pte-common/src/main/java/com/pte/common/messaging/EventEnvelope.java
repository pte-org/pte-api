package com.pte.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Base envelope every domain event is wrapped in before it reaches the broker.
 * {@code eventId} drives consumer idempotency (Kafka is at-least-once — consumers
 * dedup by this id; ADR-002). {@code traceId}/{@code tenantId} carry cross-cutting
 * context through the event backbone.
 *
 * @param <T> the service-specific event payload (defined in that service's
 *            {@code domain/event}, never shared here)
 */
public record EventEnvelope<T>(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID tenantId,
        String traceId,
        T payload) {

    public static <T> EventEnvelope<T> of(String type, UUID tenantId, String traceId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, Instant.now(), tenantId, traceId, payload);
    }
}
