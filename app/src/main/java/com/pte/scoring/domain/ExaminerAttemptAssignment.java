package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One immutable examiner owner for a submitted attempt in a session. */
@Entity
@Table(name = "examiner_attempt_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_examiner_assignment_session_attempt",
                        columnNames = {"session_public_id", "attempt_public_id"}),
                @UniqueConstraint(name = "uq_examiner_assignment_owner_scope",
                        columnNames = {"tenant_id", "session_public_id", "attempt_public_id", "examiner_public_id"})
        },
        indexes = {
                @Index(name = "idx_examiner_assignment_examiner", columnList = "tenant_id, session_public_id, examiner_public_id"),
                @Index(name = "idx_examiner_assignment_batch", columnList = "batch_public_id")
        })
@Getter
@NoArgsConstructor
public class ExaminerAttemptAssignment extends BaseEntity {

    @Column(nullable = false)
    private UUID batchPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID examinerPublicId;

    @Column(nullable = false)
    private int eligibleAnswerCount;

    @Column(nullable = false)
    private UUID assignedByPublicId;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    public ExaminerAttemptAssignment(UUID batchPublicId, UUID tenantId, UUID sessionPublicId,
            UUID attemptPublicId, UUID examinerPublicId, int eligibleAnswerCount, UUID assignedByPublicId,
            Instant assignedAt) {
        this.batchPublicId = Objects.requireNonNull(batchPublicId, ScoringDomainConstants.ASSIGNMENT_REFERENCE_REQUIRED);
        this.tenantId = Objects.requireNonNull(tenantId, ScoringDomainConstants.ASSIGNMENT_BATCH_TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId,
                ScoringDomainConstants.ASSIGNMENT_BATCH_SESSION_REQUIRED);
        this.attemptPublicId = Objects.requireNonNull(attemptPublicId, ScoringDomainConstants.ASSIGNMENT_ATTEMPT_REQUIRED);
        this.examinerPublicId = Objects.requireNonNull(examinerPublicId,
                ScoringDomainConstants.ASSIGNMENT_EXAMINER_REQUIRED);
        if (eligibleAnswerCount < 0) {
            throw new IllegalArgumentException(ScoringDomainConstants.ASSIGNMENT_ANSWER_COUNT_NEGATIVE);
        }
        this.eligibleAnswerCount = eligibleAnswerCount;
        this.assignedByPublicId = Objects.requireNonNull(assignedByPublicId,
                ScoringDomainConstants.ASSIGNMENT_ACTOR_REQUIRED);
        this.assignedAt = Objects.requireNonNull(assignedAt, ScoringDomainConstants.ASSIGNMENT_TIME_REQUIRED);
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException(ScoringDomainConstants.COMMITTED_ASSIGNMENT_IMMUTABLE);
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException(ScoringDomainConstants.COMMITTED_ASSIGNMENT_IMMUTABLE);
    }
}
