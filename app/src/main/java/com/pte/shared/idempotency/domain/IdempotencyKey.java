package com.pte.shared.idempotency.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic dedupe + receipt row. History and audit belong to {@code AuditLog}, never here: rows
 * are purged once {@code expiresAt} passes.
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_scope_key_org",
                columnNames = {"scope", "idempotency_key", "organization_id"}),
        indexes = @Index(name = "idx_idempotency_expires_at", columnList = "expires_at"))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyScope scope;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;

    /** The platform sentinel id for commands that run outside an organization; never null. */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private String result;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
