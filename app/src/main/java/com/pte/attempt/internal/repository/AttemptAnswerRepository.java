package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.AttemptAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    Optional<AttemptAnswer> findByAttemptIdAndPinnedItemId(Long attemptId, Long pinnedItemId);

    /** Scoring's pull-based ingestion (Phase 08) — every answer submitted so far for the session, any attempt. */
    @EntityGraph(attributePaths = {"pinnedItem", "attempt"})
    List<AttemptAnswer> findByAttempt_SessionPublicIdAndAttempt_TenantId(UUID sessionPublicId, UUID tenantId);
}
