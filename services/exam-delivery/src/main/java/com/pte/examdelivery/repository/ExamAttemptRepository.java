package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.ExamAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findBySessionPublicIdAndStudentPublicId(UUID sessionPublicId, UUID studentPublicId);

    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    Optional<ExamAttempt> findWithPinnedByPublicIdAndStudentPublicId(UUID publicId, UUID studentPublicId);

    /** Tenant-scoped, not student-owned — used by {@code ProctorCommandConsumer} (phase-10), where the actor is a verified proctor command, not the student. */
    Optional<ExamAttempt> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /**
     * Reporting read-model rebuild export (rabbitmq-outbox-migration Phase 9):
     * every attempt that reached {@code submittedAt} (i.e. would have fired
     * {@code AttemptSubmitted}), keyset-paginated by {@code (updatedAt, publicId)}.
     * {@code tenantId = null} means "all tenants" — callers must only pass null
     * when authorized for the bootstrap export mode (enforced at the controller).
     */
    @Query("SELECT a FROM ExamAttempt a WHERE a.submittedAt IS NOT NULL "
            + "AND (:tenantId IS NULL OR a.tenantId = :tenantId) "
            + "AND (a.updatedAt > :cursorTime OR (a.updatedAt = :cursorTime AND a.publicId > :cursorId)) "
            + "ORDER BY a.updatedAt ASC, a.publicId ASC")
    List<ExamAttempt> findSubmittedForExport(@Param("tenantId") UUID tenantId,
            @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);
}
