package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable, session-scoped assignment preview/commit snapshot. Cross-module IDs intentionally have no FK. */
@Entity
@Table(name = "examiner_assignment_batches",
        uniqueConstraints = @UniqueConstraint(name = "uq_examiner_batch_public_scope",
                columnNames = {"public_id", "tenant_id", "session_public_id"}))
@Getter
@NoArgsConstructor
public class ExaminerAssignmentBatch extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID createdByPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentBatchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentBatchStatus status;

    /** Selected program/class scope captured when Host creates the preview. */
    @Column(nullable = false, columnDefinition = "text")
    private String scopeSnapshotJson;

    /** Exact attempt-to-examiner mapping shown to Host; confirmation must not reshuffle it. */
    @Column(nullable = false, columnDefinition = "text")
    private String assignmentSnapshotJson;

    @Column
    private UUID randomSeed;

    @Column(nullable = false)
    private Instant previewExpiresAt;

    @Column
    private Instant committedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public ExaminerAssignmentBatch(UUID tenantId, UUID sessionPublicId, UUID createdByPublicId,
            AssignmentBatchMode mode, String scopeSnapshotJson, String assignmentSnapshotJson,
            UUID randomSeed, Instant previewExpiresAt) {
        this.tenantId = Objects.requireNonNull(tenantId, ScoringDomainConstants.ASSIGNMENT_BATCH_TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId,
                ScoringDomainConstants.ASSIGNMENT_BATCH_SESSION_REQUIRED);
        this.createdByPublicId = Objects.requireNonNull(createdByPublicId,
                ScoringDomainConstants.ASSIGNMENT_BATCH_CREATOR_REQUIRED);
        this.mode = Objects.requireNonNull(mode, ScoringDomainConstants.ASSIGNMENT_BATCH_MODE_REQUIRED);
        this.scopeSnapshotJson = Objects.requireNonNull(scopeSnapshotJson,
                ScoringDomainConstants.ASSIGNMENT_SCOPE_SNAPSHOT_REQUIRED);
        this.assignmentSnapshotJson = Objects.requireNonNull(assignmentSnapshotJson,
                ScoringDomainConstants.ASSIGNMENT_ALLOCATION_SNAPSHOT_REQUIRED);
        this.randomSeed = randomSeed;
        this.previewExpiresAt = Objects.requireNonNull(previewExpiresAt,
                ScoringDomainConstants.ASSIGNMENT_PREVIEW_EXPIRY_REQUIRED);
        this.status = AssignmentBatchStatus.PREVIEWED;
    }

    public boolean commit(Instant committedAt) {
        Objects.requireNonNull(committedAt, ScoringDomainConstants.ASSIGNMENT_COMMIT_TIME_REQUIRED);
        if (status != AssignmentBatchStatus.PREVIEWED) {
            throw new IllegalStateException(ScoringDomainConstants.ASSIGNMENT_BATCH_PREVIEW_REQUIRED);
        }
        if (!committedAt.isBefore(previewExpiresAt)) {
            status = AssignmentBatchStatus.EXPIRED;
            return false;
        }
        status = AssignmentBatchStatus.COMMITTED;
        this.committedAt = committedAt;
        return true;
    }

    public void expire() {
        if (status == AssignmentBatchStatus.PREVIEWED) {
            status = AssignmentBatchStatus.EXPIRED;
        }
    }

    public void markStale() {
        if (status == AssignmentBatchStatus.PREVIEWED) {
            status = AssignmentBatchStatus.STALE;
        }
    }
}
