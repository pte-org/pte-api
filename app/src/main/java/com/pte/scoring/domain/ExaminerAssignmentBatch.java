package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable, session-scoped assignment preview/commit snapshot. Cross-module IDs intentionally have no FK. */
@Entity
@Table(name = "examiner_assignment_batches")
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
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, "sessionPublicId is required");
        this.createdByPublicId = Objects.requireNonNull(createdByPublicId, "createdByPublicId is required");
        this.mode = Objects.requireNonNull(mode, "mode is required");
        this.scopeSnapshotJson = Objects.requireNonNull(scopeSnapshotJson, "scope snapshot is required");
        this.assignmentSnapshotJson = Objects.requireNonNull(assignmentSnapshotJson, "assignment snapshot is required");
        this.randomSeed = randomSeed;
        this.previewExpiresAt = Objects.requireNonNull(previewExpiresAt, "preview expiry is required");
        this.status = AssignmentBatchStatus.PREVIEWED;
    }

    public boolean commit(Instant committedAt) {
        Objects.requireNonNull(committedAt, "commit timestamp is required");
        if (status != AssignmentBatchStatus.PREVIEWED) {
            throw new IllegalStateException("Only a previewed assignment batch can be committed");
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
}
