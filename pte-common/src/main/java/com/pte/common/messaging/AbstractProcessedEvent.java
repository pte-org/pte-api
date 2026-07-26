package com.pte.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency ledger row: one per event a consumer has actually applied.
 * Kafka is at-least-once, so every {@code @KafkaListener} MUST check this
 * before applying an event and insert a row after — never trust delivery
 * count alone (ADR-002). {@code eventId} is the producer's outbox row {@code id}
 * (delivered as Debezium's {@code id} header), not a locally generated value.
 *
 * <p>Infra plumbing, not business data — same rationale as
 * {@link AbstractOutboxEntry} (2nd occurrence of this exact shape, still under
 * the DRY "3+" extraction threshold but co-located here since it's the natural
 * counterpart to the outbox it dedups against).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();
}
