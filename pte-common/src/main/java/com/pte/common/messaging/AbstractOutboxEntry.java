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
 * Shared Transactional Outbox row structure (ADR-002, superseded by the
 * RabbitMQ + polling-outbox-relay migration). Each service declares a
 * concrete {@code @Entity OutboxEntry extends AbstractOutboxEntry} mapping to its
 * own {@code outbox} table (database-per-service — the table is not shared, only
 * the column definitions).
 *
 * <p>{@code published}/{@code publishedAt}/{@code publishAttempts}/{@code lastError}/
 * {@code quarantined} are relay-side bookkeeping consumed by {@link AbstractOutboxRelay}
 * via {@link OutboxJpaRepository} — additive to the original Debezium-era columns,
 * no behavior change for services that only write via {@link AbstractOutboxWriter}.
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

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "quarantined", nullable = false)
    private boolean quarantined = false;
}
