package com.pte.scoring.repository;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringAnswerRepository extends JpaRepository<ScoringAnswer, Long> {

    Optional<ScoringAnswer> findByAnswerPublicId(UUID answerPublicId);

    List<ScoringAnswer> findBySessionPublicIdAndStatus(UUID sessionPublicId, ScoringAnswerStatus status);

    long countByAttemptPublicIdAndStatus(UUID attemptPublicId, ScoringAnswerStatus status);

    /** True while any row is still in a non-terminal state (PENDING/AI_SCORING/AI_SCORED_PENDING_REVIEW). */
    boolean existsByAttemptPublicIdAndStatusIn(UUID attemptPublicId, List<ScoringAnswerStatus> statuses);

    /**
     * Reporting read-model rebuild export (rabbitmq-outbox-migration Phase 9):
     * every answer that reached {@code SCORED} (i.e. would have fired {@code
     * AnswerScored}), keyset-paginated by {@code (updatedAt, publicId)}.
     * {@code tenantId = null} means "all tenants" — callers must only pass null
     * when authorized for the bootstrap export mode (enforced at the controller).
     */
    @Query("SELECT s FROM ScoringAnswer s WHERE s.status = 'SCORED' "
            + "AND (:tenantId IS NULL OR s.tenantId = :tenantId) "
            + "AND (s.updatedAt > :cursorTime OR (s.updatedAt = :cursorTime AND s.publicId > :cursorId)) "
            + "ORDER BY s.updatedAt ASC, s.publicId ASC")
    List<ScoringAnswer> findScoredForExport(@Param("tenantId") UUID tenantId,
            @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);
}
