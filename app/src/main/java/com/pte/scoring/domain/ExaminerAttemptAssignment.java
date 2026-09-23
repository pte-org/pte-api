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
        this.batchPublicId = Objects.requireNonNull(batchPublicId, "batchPublicId is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, "sessionPublicId is required");
        this.attemptPublicId = Objects.requireNonNull(attemptPublicId, "attemptPublicId is required");
        this.examinerPublicId = Objects.requireNonNull(examinerPublicId, "examinerPublicId is required");
        if (eligibleAnswerCount < 0) {
            throw new IllegalArgumentException("eligibleAnswerCount cannot be negative");
        }
        this.eligibleAnswerCount = eligibleAnswerCount;
        this.assignedByPublicId = Objects.requireNonNull(assignedByPublicId, "assignedByPublicId is required");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt is required");
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("Committed Examiner assignments are immutable");
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException("Committed Examiner assignments are immutable");
    }
}
