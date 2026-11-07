package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.ITEM;
import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.JOB;
import static com.pte.itembank.internal.generation.repository.GenerationJobRepository.STAGE;

/** Item changes made by the worker are guarded by the job lease, so a worker that lost its lease changes nothing. */
public interface GenerationJobItemRepository extends JpaRepository<QuestionGenerationJobItem, Long> {

    String LEASED = " and exists (select 1 from " + JOB + " j where j.id = :jobId and j.workerId = :workerId)";
    String OPEN_STAGES = " and i.stage not in (" + STAGE + ".SAVED, " + STAGE + ".FAILED, " + STAGE + ".CANCELLED)";

    List<QuestionGenerationJobItem> findByJobIdOrderByItemIndexAsc(Long jobId);

    List<QuestionGenerationJobItem> findByJobIdInOrderByItemIndexAsc(Collection<Long> jobIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = :stage, i.updatedAt = :now where i.jobId = :jobId "
            + "and i.itemIndex = :index" + OPEN_STAGES + LEASED)
    int advance(@Param("jobId") Long jobId, @Param("index") int index, @Param("stage") ItemStage stage,
                @Param("workerId") String workerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = " + STAGE + ".FAILED, i.failureCode = :code, i.failureNote = :note, "
            + "i.updatedAt = :now where i.jobId = :jobId and i.itemIndex = :index" + OPEN_STAGES + LEASED)
    int fail(@Param("jobId") Long jobId, @Param("index") int index, @Param("code") String code,
             @Param("note") String note, @Param("workerId") String workerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = " + STAGE + ".SAVED, i.questionPublicId = :questionPublicId, "
            + "i.failureCode = null, i.failureNote = null, i.updatedAt = :now "
            + "where i.jobId = :jobId and i.itemIndex = :index and i.stage <> " + STAGE + ".SAVED" + LEASED)
    int markSaved(@Param("jobId") Long jobId, @Param("index") int index,
                  @Param("questionPublicId") UUID questionPublicId, @Param("workerId") String workerId,
                  @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = " + STAGE + ".CANCELLED, i.updatedAt = :now "
            + "where i.jobId = :jobId" + OPEN_STAGES + LEASED)
    int cancelOpen(@Param("jobId") Long jobId, @Param("workerId") String workerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = " + STAGE + ".FAILED, i.failureCode = :code, "
            + "i.updatedAt = :now where i.jobId = :jobId" + OPEN_STAGES + LEASED)
    int failOpen(@Param("jobId") Long jobId, @Param("code") String code, @Param("workerId") String workerId,
                 @Param("now") Instant now);

    /** For a job nobody holds (queued, or whose worker died): no lease to check. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update " + ITEM + " i set i.stage = " + STAGE + ".CANCELLED, i.updatedAt = :now "
            + "where i.jobId = :jobId" + OPEN_STAGES)
    int cancelOpenUnleased(@Param("jobId") Long jobId, @Param("now") Instant now);
}
