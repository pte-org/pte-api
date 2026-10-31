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
import java.util.Objects;
import java.util.UUID;

/**
 * One examiner's grading of one submitted attempt: who owns it (set when the host commits the assignments, changed only
 * by the host's reassignment) and how far the examiner got (not started, in progress, finalized), with the version
 * the examiner's commands are checked against.
 */
@Entity
@Table(name = "grading_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_grading_assignment_session_attempt",
                        columnNames = {"session_public_id", "attempt_public_id"})
        },
        indexes = {
                @Index(name = "idx_grading_assignment_examiner",
                        columnList = "tenant_id, session_public_id, examiner_public_id"),
                @Index(name = "idx_grading_assignment_owner_status", columnList = "tenant_id, examiner_public_id, status")
        })
@Getter
@NoArgsConstructor
public class GradingAssignment extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID sessionPublicId;

    @Column(nullable = false, updatable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID examinerPublicId;

    @Column(nullable = false, updatable = false)
    private int eligibleAnswerCount;

    @Column(nullable = false, updatable = false)
    private UUID assignedByPublicId;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExaminerWorkflowStatus status = ExaminerWorkflowStatus.PENDING;

    /** 0 until the examiner starts; every start, finalize and reopen moves it, and commands are checked against it. */
    @Column(name = "work_version", nullable = false)
    private long workVersion;

    private Instant startedAt;

    private Instant finalizedAt;

    public GradingAssignment(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId,
            int eligibleAnswerCount, UUID assignedByPublicId, Instant assignedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, ScoringDomainConstants.ASSIGNMENT_TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId,
                ScoringDomainConstants.ASSIGNMENT_SESSION_REQUIRED);
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

    /** Whether the examiner has opened this grading; before that there is no workflow to version. */
    public boolean isStarted() {
        return workVersion > 0;
    }

    /** First start puts the work in progress at version 1; later it only moves a not-started work forward. */
    public void start(Instant startedAt) {
        if (!isStarted()) {
            this.status = ExaminerWorkflowStatus.IN_PROGRESS;
            this.startedAt = startedAt;
            this.workVersion = 1L;
        } else if (this.status == ExaminerWorkflowStatus.PENDING) {
            this.status = ExaminerWorkflowStatus.IN_PROGRESS;
            this.startedAt = startedAt;
            this.workVersion++;
        }
    }

    public void finalizeGrading(Instant finalizedAt) {
        this.status = ExaminerWorkflowStatus.COMPLETED;
        this.finalizedAt = finalizedAt;
        this.workVersion++;
    }

    public void reopen(Instant reopenedAt) {
        this.status = ExaminerWorkflowStatus.IN_PROGRESS;
        this.workVersion++;
    }

    /** The host hands the work to another examiner: progress restarts, and commands of the old version go stale. */
    public void reassign(UUID newExaminerPublicId) {
        if (status == ExaminerWorkflowStatus.COMPLETED) {
            throw new IllegalStateException(ScoringDomainConstants.ASSIGNMENT_FINALIZED_NOT_REASSIGNABLE);
        }
        this.examinerPublicId = Objects.requireNonNull(newExaminerPublicId,
                ScoringDomainConstants.ASSIGNMENT_EXAMINER_REQUIRED);
        this.status = ExaminerWorkflowStatus.PENDING;
        this.startedAt = null;
        if (isStarted()) {
            this.workVersion++;
        }
    }

    public void incrementVersion() {
        this.workVersion++;
    }
}
