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

    long countByStatusIn(Collection<JobStatus> statuses);

    boolean existsByRequestedByUserPublicIdAndStatusIn(UUID requestedBy, Collection<JobStatus> statuses);

    @Query("select coalesce(sum(j.requestedCount), 0) from " + JOB + " j where j.createdAt >= :since")
    long sumRequestedSince(@Param("since") Instant since);

    @Query("select coalesce(sum(j.requestedCount), 0) from " + JOB
            + " j where j.requestedByUserPublicId = :user and j.createdAt >= :since")
    long sumRequestedSinceByUser(@Param("user") UUID user, @Param("since") Instant since);

    /** Serializes submissions so the global caps cannot be exceeded by concurrent requests (released at commit). */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    Integer lockSubmissions(@Param("key") long key);

    /** The oldest job a worker may take: queued, or running/cancelling with an expired lease. Skips locked rows. */
    @Query(value = """
            select id from question_generation_jobs
            where deleted = false
              and (status = 'QUEUED'
                   or (status in ('RUNNING', 'CANCELLING') and lease_until < :now))
            order by created_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    List<Long> lockNextClaimable(@Param("now") Instant now);

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
            + "where j.publicId = :publicId and j.status = " + STATUS + ".QUEUED")
    int cancelQueued(@Param("publicId") UUID publicId, @Param("now") Instant now);

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
