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

    @Column(name = "request_public_id")
    private UUID requestPublicId;

    @Column(nullable = false)
    private int previousAiCount;

    @Column(nullable = false)
    private int previousExaminerCount;

    @Column(nullable = false)
    private int previousUnselectedCount;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public ScoreSourceAudit(UUID tenantId, UUID sessionPublicId, UUID actorPublicId,
            ScoreSourceSelectionScope scope, String scopeValue, ScoreSource selectedSource,
            int affectedAnswerCount, Instant occurredAt) {
        this(tenantId, sessionPublicId, actorPublicId, scope, scopeValue, selectedSource,
                affectedAnswerCount, null, 0, 0, 0, occurredAt);
    }

    public ScoreSourceAudit(UUID tenantId, UUID sessionPublicId, UUID actorPublicId,
            ScoreSourceSelectionScope scope, String scopeValue, ScoreSource selectedSource,
            int affectedAnswerCount, UUID requestPublicId, int previousAiCount,
            int previousExaminerCount, int previousUnselectedCount, Instant occurredAt) {
        this.tenantId = Objects.requireNonNull(tenantId, ScoringDomainConstants.SCORING_TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, ScoringDomainConstants.SCORING_SESSION_REQUIRED);
        this.actorPublicId = Objects.requireNonNull(actorPublicId, ScoringDomainConstants.SCORE_AUDIT_ACTOR_REQUIRED);
        this.scope = Objects.requireNonNull(scope, ScoringDomainConstants.SCORE_AUDIT_SCOPE_REQUIRED);
        if (scope == ScoreSourceSelectionScope.ALL && scopeValue != null
                || scope != ScoreSourceSelectionScope.ALL && (scopeValue == null || scopeValue.isBlank())) {
            throw new IllegalArgumentException(ScoringDomainConstants.SCORE_SOURCE_AUDIT_SCOPE_INVALID);
        }
        this.scopeValue = scopeValue;
        this.selectedSource = Objects.requireNonNull(selectedSource, ScoringDomainConstants.SCORE_AUDIT_SOURCE_REQUIRED);
        if (affectedAnswerCount < 0) {
            throw new IllegalArgumentException(ScoringDomainConstants.SCORE_SOURCE_AUDIT_COUNT_NEGATIVE);
        }
        this.affectedAnswerCount = affectedAnswerCount;
        if (previousAiCount < 0 || previousExaminerCount < 0 || previousUnselectedCount < 0) {
            throw new IllegalArgumentException(ScoringDomainConstants.SCORE_SOURCE_AUDIT_PREVIOUS_COUNTS_NEGATIVE);
        }
        this.requestPublicId = requestPublicId;
        this.previousAiCount = previousAiCount;
        this.previousExaminerCount = previousExaminerCount;
        this.previousUnselectedCount = previousUnselectedCount;
        this.occurredAt = Objects.requireNonNull(occurredAt, ScoringDomainConstants.SCORE_AUDIT_TIME_REQUIRED);
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException(ScoringDomainConstants.SCORE_SOURCE_AUDIT_APPEND_ONLY);
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException(ScoringDomainConstants.SCORE_SOURCE_AUDIT_APPEND_ONLY);
    }
}
