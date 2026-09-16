package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findBySessionPublicIdAndStudentPublicId(UUID sessionPublicId, UUID studentPublicId);

    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    Optional<ExamAttempt> findWithPinnedByPublicIdAndStudentPublicId(UUID publicId, UUID studentPublicId);

    /** No {@code pinnedSnapshot} eager-fetch — deliberately lighter than {@link #findWithPinnedByPublicIdAndStudentPublicId} for the heartbeat endpoint, which only needs ownership + status, never task content. */
    Optional<ExamAttempt> findByPublicIdAndStudentPublicId(UUID publicId, UUID studentPublicId);

    /** Serializes concurrent audio-play/answer-submit requests for the same attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ExamAttempt a WHERE a.id = :id")
    Optional<ExamAttempt> findWithLockById(@Param("id") Long id);

    /** Tenant-scoped, not student-owned — used by proctoring's force-submit (Phase 09), where the actor is a verified proctor command, not the student. */
    Optional<ExamAttempt> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** No tenant filter: reporting (Phase 10) resolves tenant from the attempt itself, then checks it against the caller — same trusted-caller pattern as session's EntitlementService. */
    @EntityGraph(attributePaths = "pinnedSnapshot")
    Optional<ExamAttempt> findByPublicIdAndStatus(UUID publicId, AttemptStatus status);

    /** Reporting's publish fanout (Phase 10) — every submitted attempt in a session. */
    @EntityGraph(attributePaths = "pinnedSnapshot")
    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndStatus(UUID sessionPublicId, UUID tenantId, AttemptStatus status);
}
