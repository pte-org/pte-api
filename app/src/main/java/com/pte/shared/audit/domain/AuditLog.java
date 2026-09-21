package com.pte.shared.audit.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Append-only audit record shared by the application's business modules. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_logs_tenant_aggregate_type", columnList = "tenant_id, aggregate_type")})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    /** Null for platform-level actions that are intentionally outside a tenant. */
    @Column(name = "tenant_id")
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
