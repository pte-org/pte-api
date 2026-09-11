package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.AttemptAnswer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    Optional<AttemptAnswer> findByAttemptIdAndPinnedItemId(Long attemptId, Long pinnedItemId);

    /**
     * Reporting read-model rebuild export (rabbitmq-outbox-migration Phase 9):
     * every answer belonging to a submitted attempt (i.e. would have fired
     * {@code AnswerSubmitted}), keyset-paginated by {@code (updatedAt, publicId)}.
     * {@code tenantId = null} means "all tenants" — callers must only pass null
     * when authorized for the bootstrap export mode (enforced at the controller).
     * Fetch-joins {@code attempt}/{@code pinnedItem} to avoid N+1 when the
     * caller maps to the export DTO (needs attemptPublicId/tenantId/taskType).
     */
    @Query("SELECT a FROM AttemptAnswer a JOIN FETCH a.attempt att JOIN FETCH a.pinnedItem "
            + "WHERE att.submittedAt IS NOT NULL "
            + "AND (:tenantId IS NULL OR att.tenantId = :tenantId) "
            + "AND (a.updatedAt > :cursorTime OR (a.updatedAt = :cursorTime AND a.publicId > :cursorId)) "
            + "ORDER BY a.updatedAt ASC, a.publicId ASC")
    List<AttemptAnswer> findSubmittedForExport(@Param("tenantId") UUID tenantId,
            @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);
}
