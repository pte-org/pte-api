package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoringAnswer;
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

public interface ScoringAnswerRepository extends JpaRepository<ScoringAnswer, Long> {

    /** Overview: non-deleted answers of the tenant grouped by scoring status (counts only, never rows). */
    @Query("SELECT s.status, COUNT(s) FROM ScoringAnswer s "
            + "WHERE s.tenantId = :tenantId AND s.deleted = false GROUP BY s.status")
    List<Object[]> countByTenantGroupedByStatus(@Param("tenantId") UUID tenantId);

    org.springframework.data.domain.Slice<StaffAnswerRow>
            findByTenantIdAndAttemptPublicIdInAndDeletedFalse(UUID tenantId, List<UUID> attemptIds, Pageable pageable);

    Optional<ScoringAnswer> findByAnswerPublicId(UUID answerPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScoringAnswer s WHERE s.answerPublicId = :answerPublicId")
    Optional<ScoringAnswer> findByAnswerPublicIdForUpdate(@Param("answerPublicId") UUID answerPublicId);

    /**
     * What a host's scoring request works on: new answers, failed AI answers,
     * and AI answers whose job was lost — dispatched before {@code staleBefore},
     * or before dispatch times were recorded. A job still in flight is left
     * alone; dispatching it again would pay the vendor twice.
     */
    @Query("SELECT s FROM ScoringAnswer s WHERE s.sessionPublicId = :sessionPublicId AND s.tenantId = :tenantId "
            + "AND (s.status IN (com.pte.scoring.domain.enums.ScoringAnswerStatus.PENDING, "
            + "com.pte.scoring.domain.enums.ScoringAnswerStatus.SCORING_FAILED) "
            + "OR (s.status = com.pte.scoring.domain.enums.ScoringAnswerStatus.AI_SCORING "
            + "AND (s.aiDispatchedAt IS NULL OR s.aiDispatchedAt < :staleBefore)))")
    List<ScoringAnswer> findScoringWork(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId, @Param("staleBefore") Instant staleBefore);

    List<ScoringAnswer> findBySessionPublicIdAndTenantId(UUID sessionPublicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScoringAnswer s WHERE s.sessionPublicId = :sessionPublicId "
            + "AND s.tenantId = :tenantId ORDER BY s.id")
    List<ScoringAnswer> findSessionAnswersForUpdate(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId);

    List<ScoringAnswer> findByAttemptPublicIdAndTenantId(UUID attemptPublicId, UUID tenantId);

    List<ScoringAnswer> findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
            UUID sessionPublicId, UUID tenantId, List<UUID> attemptPublicIds);

    /** Reporting's skill-aggregation pull (Phase 10) — every scored answer for one attempt. */
    List<ScoringAnswer> findByAttemptPublicIdAndTenantIdAndStatus(UUID attemptPublicId, UUID tenantId,
            ScoringAnswerStatus status);

    /**
     * Host review list — tenantId is always required (a host is always
     * tenant-scoped). sessionPublicId/status are optional narrowing filters.
     */
    @Query("SELECT s FROM ScoringAnswer s WHERE s.tenantId = :tenantId "
            + "AND (:sessionPublicId IS NULL OR s.sessionPublicId = :sessionPublicId) "
            + "AND (:attemptPublicId IS NULL OR s.attemptPublicId = :attemptPublicId) "
            + "AND (:status IS NULL OR s.status = :status) "
            + "ORDER BY s.createdAt DESC")
    Page<ScoringAnswer> findForReview(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("attemptPublicId") UUID attemptPublicId,
            @Param("status") ScoringAnswerStatus status, Pageable pageable);

    @Query("""
            SELECT s FROM ScoringAnswer s WHERE s.tenantId = :tenantId
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
    Page<ScoringAnswer> findForReviewWithSearch(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("attemptPublicId") UUID attemptPublicId,
            @Param("status") ScoringAnswerStatus status, @Param("search") String search,
            @Param("searchStatus") ScoringAnswerStatus searchStatus, @Param("searchUuid") UUID searchUuid,
            Pageable pageable);

    /** Answer counts per attempt and status for one session — the host's per-attempt submissions list. */
    @Query("SELECT new com.pte.scoring.internal.repository.AttemptStatusCount(s.attemptPublicId, s.status, COUNT(s)) "
            + "FROM ScoringAnswer s WHERE s.tenantId = :tenantId AND s.sessionPublicId = :sessionPublicId "
            + "GROUP BY s.attemptPublicId, s.status")
    List<AttemptStatusCount> countByAttemptAndStatus(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);
}
