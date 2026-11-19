package com.pte.itembank.internal.generation.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One author request for many questions, split into child jobs that run one after another. Like the job, state
 * changes after creation go through guarded bulk updates under the batch row lock, never read-modify-write.
 */
@Entity
@Table(name = "question_generation_batches")
@Getter
@Setter
@NoArgsConstructor
public class QuestionGenerationBatch extends BaseEntity {

    @Column(name = "requested_by_user_public_id", nullable = false, updatable = false)
    private UUID requestedByUserPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private QuestionPool pool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rows", nullable = false, updatable = false)
    private List<BatchRow> rows = new ArrayList<>();

    @Column(name = "total_requested", nullable = false, updatable = false)
    private int totalRequested;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BatchStatus status = BatchStatus.QUEUED;

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "cancelled_count", nullable = false)
    private int cancelledCount;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
