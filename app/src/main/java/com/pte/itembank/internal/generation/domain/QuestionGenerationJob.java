package com.pte.itembank.internal.generation.domain;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One admin request to generate questions. State changes after creation go through conditional bulk updates in
 * the repository (never read-modify-write of this entity) so the worker, a cancel request and the lease renewal
 * cannot overwrite each other.
 */
@Entity
@Table(name = "question_generation_jobs")
@Getter
@Setter
@NoArgsConstructor
public class QuestionGenerationJob extends BaseEntity {

    @Column(name = "requested_by_user_public_id", nullable = false, updatable = false)
    private UUID requestedByUserPublicId;

    @Column(name = "task_type_key", nullable = false, updatable = false, length = 64)
    private String taskTypeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "cefr_level", nullable = false, updatable = false, length = 2)
    private CefrLevel cefrLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private QuestionPool pool;

    @Column(name = "topic_hint", updatable = false, length = 500)
    private String topicHint;

    /** Describe Image only: a chart from data, an AI-painted picture, or AUTO from the topic. */
    @Enumerated(EnumType.STRING)
    @Column(name = "image_kind", nullable = false, updatable = false, length = 16)
    private ImageKind imageKind = ImageKind.AUTO;

    @Column(name = "requested_count", nullable = false, updatable = false)
    private int requestedCount;

    /** False for a job started from the question form: its question goes into the form, not into the bank. */
    @Column(name = "save_questions", nullable = false, updatable = false)
    private boolean saveQuestions = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "worker_id", length = 64)
    private String workerId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;
}
