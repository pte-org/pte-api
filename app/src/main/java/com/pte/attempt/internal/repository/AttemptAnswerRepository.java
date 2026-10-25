package com.pte.attempt.internal.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.pte.attempt.domain.AttemptAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    Optional<AttemptAnswer> findByAttemptIdAndItemPublicId(Long attemptId, UUID itemPublicId);

    /** Scoring's pull-based ingestion (Phase 08) — every answer submitted so far for the session, any attempt. */
    @EntityGraph(attributePaths = "attempt")
    List<AttemptAnswer> findByAttempt_SessionPublicIdAndAttempt_TenantId(UUID sessionPublicId, UUID tenantId);

    /** Scoring's ingestion of one just-submitted attempt, without scanning the whole session. */
    @EntityGraph(attributePaths = "attempt")
    List<AttemptAnswer> findByAttempt_PublicIdAndAttempt_TenantId(UUID attemptPublicId, UUID tenantId);

    /** One answer by its public id, tenant-scoped; the attempt is fetched for ownership and exam version. */
    @EntityGraph(attributePaths = "attempt")
    Optional<AttemptAnswer> findByPublicIdAndAttempt_TenantId(UUID publicId, UUID tenantId);

    /** The answers a score-source choice touches, locked so concurrent choices serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "attempt")
    List<AttemptAnswer> findAllByPublicIdInAndAttempt_TenantId(java.util.Collection<UUID> publicIds, UUID tenantId);

    /** Only answers that have a chosen source; an absent answer has none. */
    @Query("select a.publicId as answerPublicId, "
            + "a.selectedScoreSource as source, a.selectedScoreSourceAt as selectedAt, "
            + "a.selectedScoreSourceByPublicId as selectedByPublicId, "
            + "a.selectedEvaluationPublicId as evaluationPublicId from AttemptAnswer a "
            + "where a.attempt.sessionPublicId = :sessionPublicId and a.attempt.tenantId = :tenantId "
            + "and a.selectedScoreSource is not null")
    List<SelectionRow> findSelections(@Param("sessionPublicId")
            UUID sessionPublicId, @Param("tenantId") UUID tenantId);

    interface SelectionRow {
        UUID getAnswerPublicId();

        String getSource();

        java.time.Instant getSelectedAt();

        UUID getSelectedByPublicId();

        UUID getEvaluationPublicId();
    }
}
