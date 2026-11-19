package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.ITEM;
import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.JOB;
import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.STAGE;

/**
 * Batches. Lock order everywhere is batch row first, then its job rows ({@link #findByIdForUpdate}), so cancel,
 * a child finishing and the reconciler cannot deadlock.
 */
public interface GenerationBatchRepository extends JpaRepository<QuestionGenerationBatch, Long> {

    String BATCH = "com.pte.itembank.internal.generation.domain.QuestionGenerationBatch";
    String BATCH_STATUS = "com.pte.itembank.internal.generation.domain.BatchStatus";

    Optional<QuestionGenerationBatch> findByPublicIdAndDeletedFalse(UUID publicId);

    boolean existsByRequestedByUserPublicIdAndStatusIn(UUID requestedBy, Collection<BatchStatus> statuses);

    Page<QuestionGenerationBatch> findByDeletedFalse(Pageable pageable);

    Page<QuestionGenerationBatch> findByRequestedByUserPublicIdAndDeletedFalse(UUID requestedBy, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from " + BATCH + " b where b.id = :id")
    Optional<QuestionGenerationBatch> findByIdForUpdate(@Param("id") Long id);

    /** Open batches none of whose children is still open: the reconciler's safety net for a missed terminal hook. */
    @Query(value = """
            select b.id from question_generation_batches b
            where b.status in ('QUEUED', 'RUNNING', 'CANCELLING')
              and not exists (select 1 from question_generation_jobs j
                              where j.batch_id = b.id and j.status in ('PENDING', 'QUEUED', 'RUNNING', 'CANCELLING'))
            """, nativeQuery = true)
    List<Long> findOpenIdsWithoutOpenChild();

    /** The first child was claimed. No-op for a batch already running or being cancelled. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + BATCH + " b set b.status = " + BATCH_STATUS + ".RUNNING, b.startedAt = coalesce(b.startedAt, :now), "
            + "b.updatedAt = :now where b.id = :id and b.status = " + BATCH_STATUS + ".QUEUED")
    int markRunning(@Param("id") Long id, @Param("now") Instant now);

    /** Open batches whose children are all PENDING: a promotion was lost (the reconciler queues the next child). */
    @Query(value = """
            select b.id from question_generation_batches b
            where b.status in ('QUEUED', 'RUNNING', 'CANCELLING')
              and exists (select 1 from question_generation_jobs j where j.batch_id = b.id and j.status = 'PENDING')
              and not exists (select 1 from question_generation_jobs j
                              where j.batch_id = b.id and j.status in ('QUEUED', 'RUNNING', 'CANCELLING'))
            """, nativeQuery = true)
    List<Long> findOpenIdsWithOnlyPendingChildren();

    /** Cancel requested: an open batch becomes CANCELLING until its running child stops. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + BATCH + " b set b.cancelRequested = true, b.status = " + BATCH_STATUS + ".CANCELLING, "
            + "b.updatedAt = :now where b.id = :id and b.status in (" + BATCH_STATUS + ".QUEUED, " + BATCH_STATUS
            + ".RUNNING)")
    int requestCancel(@Param("id") Long id, @Param("now") Instant now);

    /** Final status, computed from the children. Guarded: a batch is closed once. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + BATCH + " b set b.status = :status, b.failureCode = coalesce(:failureCode, b.failureCode), "
            + "b.finishedAt = :now, b.updatedAt = :now where b.id = :id and b.status in (" + BATCH_STATUS + ".QUEUED, "
            + BATCH_STATUS + ".RUNNING, " + BATCH_STATUS + ".CANCELLING)")
    int close(@Param("id") Long id, @Param("status") BatchStatus status, @Param("failureCode") String failureCode,
              @Param("now") Instant now);

    /** Counters derived from the items of every child, never counted up (no drift). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + BATCH + " b set "
            + "b.succeededCount = (select count(i) from " + ITEM + " i where i.jobId in (select j.id from " + JOB
            + " j where j.batchId = b.id) and i.stage in (" + STAGE + ".READY, " + STAGE + ".SAVED)), "
            + "b.failedCount = (select count(i) from " + ITEM + " i where i.jobId in (select j.id from " + JOB
            + " j where j.batchId = b.id) and i.stage = " + STAGE + ".FAILED), "
            + "b.cancelledCount = (select count(i) from " + ITEM + " i where i.jobId in (select j.id from " + JOB
            + " j where j.batchId = b.id) and i.stage = " + STAGE + ".CANCELLED), "
            + "b.updatedAt = :now where b.id = :id")
    int refreshCounters(@Param("id") Long id, @Param("now") Instant now);
}
