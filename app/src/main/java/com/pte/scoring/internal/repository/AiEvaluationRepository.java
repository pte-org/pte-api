package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    /** Overview: non-deleted answers of the tenant grouped by scoring status (counts only, never rows). */
    @Query("SELECT s.status, COUNT(s) FROM AiEvaluation s "
            + "WHERE s.tenantId = :tenantId AND s.deleted = false GROUP BY s.status")
    List<Object[]> countByTenantGroupedByStatus(@Param("tenantId") UUID tenantId);

    org.springframework.data.domain.Slice<StaffAnswerRow>
            findByTenantIdAndAttemptPublicIdInAndDeletedFalse(UUID tenantId, List<UUID> attemptIds, Pageable pageable);

    Optional<AiEvaluation> findByAnswerPublicId(UUID answerPublicId);

    /** Read side: always keyed by the verified answer own tenant, never a client-supplied id. */
    Optional<AiEvaluation> findByAnswerPublicIdAndTenantId(UUID answerPublicId, UUID tenantId);

    List<AiEvaluation> findByTenantIdAndAnswerPublicIdIn(UUID tenantId, java.util.Collection<UUID> answerPublicIds);

    /** Only rows that carry a flag; an answer missing from the result has none. */
    @Query("SELECT new com.pte.scoring.internal.repository.AiDetailFlags(e.answerPublicId, e.fallback, "
            + "e.failureReason) FROM AiEvaluation e WHERE e.tenantId = :tenantId "
            + "AND e.answerPublicId IN :answerPublicIds AND (e.fallback = true OR e.failureReason IS NOT NULL)")
    List<AiDetailFlags> findFlags(@Param("tenantId") UUID tenantId,
            @Param("answerPublicIds") java.util.Collection<UUID> answerPublicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AiEvaluation s WHERE s.answerPublicId = :answerPublicId")
    Optional<AiEvaluation> findByAnswerPublicIdForUpdate(@Param("answerPublicId") UUID answerPublicId);

    /**
     * What a host's scoring request works on: new answers, failed AI answers,
     * and AI answers whose job was lost — dispatched before {@code staleBefore},
     * or before dispatch times were recorded. A job still in flight is left
     * alone; dispatching it again would pay the vendor twice.
     */
    @Query("SELECT s FROM AiEvaluation s WHERE s.sessionPublicId = :sessionPublicId AND s.tenantId = :tenantId "
            + "AND (s.status IN (com.pte.scoring.domain.enums.ScoringAnswerStatus.PENDING, "
            + "com.pte.scoring.domain.enums.ScoringAnswerStatus.SCORING_FAILED) "
            + "OR (s.status = com.pte.scoring.domain.enums.ScoringAnswerStatus.AI_SCORING "
            + "AND (s.aiDispatchedAt IS NULL OR s.aiDispatchedAt < :staleBefore)))")
    List<AiEvaluation> findScoringWork(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId, @Param("staleBefore") Instant staleBefore);

    List<AiEvaluation> findBySessionPublicIdAndTenantId(UUID sessionPublicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AiEvaluation s WHERE s.sessionPublicId = :sessionPublicId "
            + "AND s.tenantId = :tenantId ORDER BY s.id")
    List<AiEvaluation> findSessionAnswersForUpdate(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId);

    List<AiEvaluation> findByAttemptPublicIdAndTenantId(UUID attemptPublicId, UUID tenantId);

    List<AiEvaluation> findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
            UUID sessionPublicId, UUID tenantId, List<UUID> attemptPublicIds);

    boolean existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndAnswerPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId);

    List<AiEvaluation> findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId);

    /** Reporting's skill-aggregation pull (Phase 10) — every scored answer for one attempt. */
    List<AiEvaluation> findByAttemptPublicIdAndTenantIdAndStatus(UUID attemptPublicId, UUID tenantId,
            ScoringAnswerStatus status);

    /**
     * Host review list — tenantId is always required (a host is always
     * tenant-scoped). sessionPublicId/status are optional narrowing filters.
     */
    @Query("SELECT s FROM AiEvaluation s WHERE s.tenantId = :tenantId "
            + "AND (:sessionPublicId IS NULL OR s.sessionPublicId = :sessionPublicId) "
            + "AND (:attemptPublicId IS NULL OR s.attemptPublicId = :attemptPublicId) "
            + "AND (:status IS NULL OR s.status = :status) "
            + "ORDER BY s.createdAt DESC")
    Page<AiEvaluation> findForReview(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("attemptPublicId") UUID attemptPublicId,
            @Param("status") ScoringAnswerStatus status, Pageable pageable);

    @Query("""
            SELECT s FROM AiEvaluation s WHERE s.tenantId = :tenantId
              AND (:sessionPublicId IS NULL OR s.sessionPublicId = :sessionPublicId)
              AND (:attemptPublicId IS NULL OR s.attemptPublicId = :attemptPublicId)
              AND (:status IS NULL OR s.status = :status)
              AND (:search IS NULL
                   OR lower(s.taskType) like lower(concat('%', :search, '%'))
                   OR (:searchStatus IS NOT NULL AND s.status = :searchStatus)
                   OR (:searchUuid IS NOT NULL AND (s.answerPublicId = :searchUuid
                       OR s.attemptPublicId = :searchUuid)))
              ORDER BY s.createdAt DESC
            """)
    Page<AiEvaluation> findForReviewWithSearch(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("attemptPublicId") UUID attemptPublicId,
            @Param("status") ScoringAnswerStatus status, @Param("search") String search,
            @Param("searchStatus") ScoringAnswerStatus searchStatus, @Param("searchUuid") UUID searchUuid,
            Pageable pageable);

    /** Answer counts per attempt and status for one session — the host's per-attempt submissions list. */
    @Query("SELECT new com.pte.scoring.internal.repository.AttemptStatusCount(s.attemptPublicId, s.status, COUNT(s)) "
            + "FROM AiEvaluation s WHERE s.tenantId = :tenantId AND s.sessionPublicId = :sessionPublicId "
            + "GROUP BY s.attemptPublicId, s.status")
    List<AttemptStatusCount> countByAttemptAndStatus(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);
}
