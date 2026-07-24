package com.pte.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared Transactional Outbox row structure (ADR-002). Each service declares a
 * concrete {@code @Entity OutboxEntry extends AbstractOutboxEntry} mapping to its
 * own {@code outbox} table (database-per-service — the table is not shared, only
 * the column definitions). Columns follow the Debezium outbox-event-router
 * convention so Phase 6 CDC wiring is uniform.
 *
 * <p>Infrastructure plumbing, not a business entity — belongs in pte-common the
 * same way {@code BaseEntity} does.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
