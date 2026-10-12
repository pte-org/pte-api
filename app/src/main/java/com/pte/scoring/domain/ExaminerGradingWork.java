package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
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

import java.time.Instant;
import java.util.UUID;

/** Per-assignment workflow tracking progress, version, and timestamps. */
@Entity
@Table(name = "examiner_grading_work",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_examiner_grading_work_assignment",
                        columnNames = {"tenant_id", "assignment_public_id"})
        },
        indexes = {
                @Index(name = "idx_examiner_grading_work_owner", columnList = "tenant_id, examiner_public_id, status"),
                @Index(name = "idx_examiner_grading_work_target", columnList = "tenant_id, session_public_id, attempt_public_id")
        })
@Getter
@NoArgsConstructor
public class ExaminerGradingWork extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID assignmentPublicId;

    @Column(nullable = false)
    private UUID examinerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExaminerWorkflowStatus status;

    @Column(nullable = false)
    private long version;

    private Instant startedAt;

    private Instant finalizedAt;

    public ExaminerGradingWork(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID assignmentPublicId, UUID examinerPublicId, Instant startedAt) {
        this.tenantId = tenantId;
        this.sessionPublicId = sessionPublicId;
        this.attemptPublicId = attemptPublicId;
        this.assignmentPublicId = assignmentPublicId;
        this.examinerPublicId = examinerPublicId;
        this.status = ExaminerWorkflowStatus.IN_PROGRESS;
        this.version = 1L;
        this.startedAt = startedAt;
    }

    public void start(Instant startedAt) {
        if (this.status == ExaminerWorkflowStatus.PENDING) {
            this.status = ExaminerWorkflowStatus.IN_PROGRESS;
            this.startedAt = startedAt;
            this.version++;
        }
    }

    public void finalizeGrading(Instant finalizedAt) {
        this.status = ExaminerWorkflowStatus.COMPLETED;
        this.finalizedAt = finalizedAt;
        this.version++;
    }

    public void incrementVersion() {
        this.version++;
    }
}
