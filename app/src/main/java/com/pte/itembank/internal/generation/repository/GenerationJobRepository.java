package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every state change is a conditional bulk update guarded by the owning worker or the expected state, so a
 * cancel request, a lease renewal and the worker's progress can never overwrite one another.
 */
public interface GenerationJobRepository extends JpaRepository<QuestionGenerationJob, Long> {

    String JOB = "com.pte.itembank.internal.generation.domain.QuestionGenerationJob";
    String ITEM = "com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem";
    String STATUS = "com.pte.itembank.internal.generation.domain.JobStatus";
    String STAGE = "com.pte.itembank.internal.generation.domain.ItemStage";

    Optional<QuestionGenerationJob> findByPublicIdAndDeletedFalse(UUID publicId);

    Page<QuestionGenerationJob> findByDeletedFalse(Pageable pageable);

    Page<QuestionGenerationJob> findByRequestedByUserPublicIdAndDeletedFalse(UUID requestedBy, Pageable pageable);

    /** The job list without batch children (they are shown inside their batch). */
    Page<QuestionGenerationJob> findByBatchIdIsNullAndDeletedFalse(Pageable pageable);

    Page<QuestionGenerationJob> findByRequestedByUserPublicIdAndBatchIdIsNullAndDeletedFalse(UUID requestedBy,
                                                                                            Pageable pageable);

    boolean existsByRequestedByUserPublicIdAndStatusIn(UUID requestedBy, Collection<JobStatus> statuses);

    boolean existsByRequestedByUserPublicIdAndSaveQuestionsTrueAndStatusIn(UUID requestedBy,
                                                                          Collection<JobStatus> statuses);

    /**
     * Quota usage. A job cancelled before it ever started (a batch child stopped by cancel or a fatal failure, or a
     * queued job cancelled) is refunded; a job that started counts in full, even for items that failed.
     */
    String NOT_REFUNDED = " and not (j.status = " + STATUS + ".CANCELLED and j.startedAt is null)";

    @Query("select coalesce(sum(j.requestedCount), 0) from " + JOB + " j where j.createdAt >= :since" + NOT_REFUNDED)
    long sumRequestedSince(@Param("since") Instant since);

    @Query("select coalesce(sum(j.requestedCount), 0) from " + JOB
            + " j where j.requestedByUserPublicId = :user and j.createdAt >= :since" + NOT_REFUNDED)
    long sumRequestedSinceByUser(@Param("user") UUID user, @Param("since") Instant since);

    /** Serializes submissions so the global caps cannot be exceeded by concurrent requests (released at commit). */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    Integer lockSubmissions(@Param("key") long key);

    /** Serializes claims across instances so the concurrency cap is checked and taken atomically (released at commit). */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    Integer lockClaims(@Param("key") long key);

    /**
     * A job whose worker died (lease expired). Plain select: claimers are serialized by {@link #lockClaims} and every
     * later change is a guarded update, so no job row lock is held before a batch row lock (batch-first lock order).
     */
    @Query(value = """
            select id from question_generation_jobs
            where deleted = false and status in ('RUNNING', 'CANCELLING') and lease_until < :now
            order by lease_until
            limit 1
            """, nativeQuery = true)
    List<Long> findNextResumable(@Param("now") Instant now);

    /** The next queued job: standalone and preview jobs first, then batch children in batch and run order. */
    @Query(value = """
            select id from question_generation_jobs
            where deleted = false and status = 'QUEUED'
            order by (batch_id is not null), created_at, batch_seq, id
            limit 1
            """, nativeQuery = true)
    List<Long> findNextQueued();

    /** Jobs a worker currently holds a live lease on: what the concurrency cap counts. */
    @Query("select count(j) from " + JOB + " j where j.deleted = false and j.status in (" + STATUS + ".RUNNING, "
            + STATUS + ".CANCELLING) and j.leaseUntil >= :now")
    long countLiveLeases(@Param("now") Instant now);

    @Query("select j.batchId from " + JOB + " j where j.id = :id")
    List<Long> findBatchIdById(@Param("id") Long id);

    /** Questions of the given task types that the jobs of one batch run before the job at {@code seq}. */
    @Query("select coalesce(sum(j.requestedCount), 0) from " + JOB + " j where j.batchId = :batchId "
            + "and j.batchSeq < :seq and j.taskTypeKey in :taskTypeKeys")
    long sumRequestedCountBefore(@Param("batchId") Long batchId, @Param("seq") int seq,
                                 @Param("taskTypeKeys") java.util.Collection<String> taskTypeKeys);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = " + STATUS + ".RUNNING, j.workerId = :workerId, "
            + "j.leaseUntil = :leaseUntil, j.startedAt = coalesce(j.startedAt, :now) "
            + "where j.id = :id and j.status in (" + STATUS + ".QUEUED, " + STATUS + ".RUNNING)")
    int markRunning(@Param("id") Long id, @Param("workerId") String workerId,
                    @Param("leaseUntil") Instant leaseUntil, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.leaseUntil = :leaseUntil where j.id = :id and j.workerId = :workerId "
            + "and j.status in (" + STATUS + ".RUNNING, " + STATUS + ".CANCELLING)")
    int renewLease(@Param("id") Long id, @Param("workerId") String workerId,
                   @Param("leaseUntil") Instant leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = " + STATUS + ".CANCELLED, j.finishedAt = :now "
            + "where j.publicId = :publicId and j.status in (" + STATUS + ".PENDING, " + STATUS + ".QUEUED)")
    int cancelQueued(@Param("publicId") UUID publicId, @Param("now") Instant now);

    List<QuestionGenerationJob> findByBatchIdOrderByBatchSeqAsc(Long batchId);

    boolean existsByBatchIdAndStatusIn(Long batchId, Collection<JobStatus> statuses);

    /**
     * Lets up to {@code limit} more children of the batch run: the PENDING children with the lowest seq become
     * QUEUED. Guarded on PENDING, so a repeated call never promotes a child twice nor touches a running one.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update question_generation_jobs set status = 'QUEUED'
            where id in (select id from question_generation_jobs
                         where batch_id = :batchId and status = 'PENDING' and deleted = false
                         order by batch_seq limit :limit)
              and status = 'PENDING'
            """, nativeQuery = true)
    int promoteNext(@Param("batchId") Long batchId, @Param("limit") int limit);

    long countByBatchIdAndStatusIn(Long batchId, Collection<JobStatus> statuses);

    /** Closes the children nobody holds (waiting or queued). started_at stays null, so their quota is refunded. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = " + STATUS + ".CANCELLED, j.finishedAt = :now, "
            + "j.failureCode = :failureCode where j.batchId = :batchId and j.status in (" + STATUS + ".PENDING, "
            + STATUS + ".QUEUED)")
    int cancelPending(@Param("batchId") Long batchId, @Param("now") Instant now,
                      @Param("failureCode") String failureCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = " + STATUS + ".CANCELLING "
            + "where j.publicId = :publicId and j.status in (" + STATUS + ".RUNNING, " + STATUS + ".CANCELLING)")
    int markCancelling(@Param("publicId") UUID publicId);

    /** A cancel request whose worker died: nobody is left to honour it, so close the job. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = " + STATUS + ".CANCELLED, j.finishedAt = :now, j.workerId = null "
            + "where j.id = :id and j.status = " + STATUS + ".CANCELLING")
    int finishStaleCancelling(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set j.status = :status, j.failureCode = :failureCode, j.finishedAt = :now "
            + "where j.id = :id and j.workerId = :workerId "
            + "and j.status in (" + STATUS + ".RUNNING, " + STATUS + ".CANCELLING)")
    int finish(@Param("id") Long id, @Param("workerId") String workerId, @Param("status") JobStatus status,
               @Param("failureCode") String failureCode, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + JOB + " j set "
            + "j.succeededCount = (select count(i) from " + ITEM + " i where i.jobId = j.id and i.stage in ("
            + STAGE + ".READY, " + STAGE + ".SAVED)), "
            + "j.failedCount = (select count(i) from " + ITEM + " i where i.jobId = j.id and i.stage = "
            + STAGE + ".FAILED) where j.id = :id")
    int refreshCounters(@Param("id") Long id);

    /** Status of the job when {@code workerId} still holds its lease; empty when the lease was lost. */
    @Query("select j.status from " + JOB + " j where j.id = :id and j.workerId = :workerId "
            + "and j.status in (" + STATUS + ".RUNNING, " + STATUS + ".CANCELLING)")
    List<JobStatus> statusIfLeaseHeld(@Param("id") Long id, @Param("workerId") String workerId);
}
