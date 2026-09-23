package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only audit fact for a Host source-selection operation. */
@Entity
@Table(name = "score_source_audits", indexes = {
        @Index(name = "idx_score_source_audit_session", columnList = "tenant_id, session_public_id, occurred_at")
})
@Getter
@NoArgsConstructor
public class ScoreSourceAudit extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID actorPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScoreSourceSelectionScope scope;

    @Column(length = 128)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScoreSource selectedSource;

    @Column(nullable = false)
    private int affectedAnswerCount;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public ScoreSourceAudit(UUID tenantId, UUID sessionPublicId, UUID actorPublicId,
            ScoreSourceSelectionScope scope, String scopeValue, ScoreSource selectedSource,
            int affectedAnswerCount, Instant occurredAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, "sessionPublicId is required");
        this.actorPublicId = Objects.requireNonNull(actorPublicId, "actorPublicId is required");
        this.scope = Objects.requireNonNull(scope, "scope is required");
        if (scope == ScoreSourceSelectionScope.ALL && scopeValue != null
                || scope != ScoreSourceSelectionScope.ALL && (scopeValue == null || scopeValue.isBlank())) {
            throw new IllegalArgumentException("Score-source audit scope value does not match its scope");
        }
        this.scopeValue = scopeValue;
        this.selectedSource = Objects.requireNonNull(selectedSource, "selectedSource is required");
        if (affectedAnswerCount < 0) {
            throw new IllegalArgumentException("Affected answer count cannot be negative");
        }
        this.affectedAnswerCount = affectedAnswerCount;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("Score-source audit rows are append-only");
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException("Score-source audit rows are append-only");
    }
}
