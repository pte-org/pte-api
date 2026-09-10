package com.pte.admin.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An append-only ledger row for a Host-initiated mutation — genuinely
 * separate from the outbox (transport-only, pruned daily by
 * {@code AdminOutboxCleanupJob}), same "immutable ledger, never updated
 * after creation" shape as {@link QuotaTransaction}. {@code aggregateType}/
 * {@code aggregateId} identify the mutated entity (same values used for the
 * accompanying {@code outboxWriter.write(...)} call at the same call site);
 * {@code action} reuses the matching {@code AdminConstants.EVENT_*} constant
 * for 1:1 traceability with the outbox event; {@code summary} is a
 * human-readable description for the audit log UI. No back-reference
 * collection on {@link Tenant} on purpose — nothing needs to navigate
 * tenant→logs in memory, only tenant→[persisted rows via repository], same
 * reasoning as {@code QuotaTransaction} omitting one.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_logs_tenant_aggregate_type", columnList = "tenant_id, aggregate_type")})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 500)
    private String summary;
}
