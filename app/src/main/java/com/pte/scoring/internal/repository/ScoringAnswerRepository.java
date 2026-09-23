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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringAnswerRepository extends JpaRepository<ScoringAnswer, Long> {

    Optional<ScoringAnswer> findByAnswerPublicId(UUID answerPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScoringAnswer s WHERE s.answerPublicId = :answerPublicId")
    Optional<ScoringAnswer> findByAnswerPublicIdForUpdate(@Param("answerPublicId") UUID answerPublicId);

    List<ScoringAnswer> findBySessionPublicIdAndTenantIdAndStatus(UUID sessionPublicId, UUID tenantId,
            ScoringAnswerStatus status);

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
            + "AND (:status IS NULL OR s.status = :status) "
            + "ORDER BY s.createdAt DESC")
    Page<ScoringAnswer> findForReview(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("status") ScoringAnswerStatus status,
            Pageable pageable);
}
