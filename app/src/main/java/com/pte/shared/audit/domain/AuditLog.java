package com.pte.shared.audit.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** Human-readable resource identity captured when the audit event is written. */
    @Column(name = "resource_name", length = 255)
    private String resourceName;

    /** Stable business reference shown to users instead of the internal aggregate UUID. */
    @Column(name = "resource_code", length = 255)
    private String resourceCode;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 500)
    private String summary;

    /** Comma-joined roles the actor held when the event was written. */
    @Column(name = "actor_role", length = 255)
    private String actorRole;

    /** Client request id, so a receipt or history lookup can be tied back to one command. */
    @Column(name = "request_public_id")
    private UUID requestPublicId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private String afterState;
}
