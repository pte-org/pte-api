package com.pte.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox row (ADR-002), written in the same TX as the business
 * change. Debezium (Phase 6) relays to Kafka. (This mirrors the iam outbox; a
 * shared mapped-superclass will be extracted to pte-common at the 3rd service.)
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEntry {

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

    public static OutboxEntry of(String aggregateType, String aggregateId, String eventType,
                                 String payload, UUID tenantId) {
        OutboxEntry entry = new OutboxEntry();
        entry.aggregateType = aggregateType;
        entry.aggregateId = aggregateId;
        entry.eventType = eventType;
        entry.payload = payload;
        entry.tenantId = tenantId;
        entry.occurredAt = Instant.now();
        return entry;
    }
}
