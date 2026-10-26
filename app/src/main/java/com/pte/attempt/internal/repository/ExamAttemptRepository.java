package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long>,
        JpaSpecificationExecutor<ExamAttempt> {

    @Query("select a.publicId as attemptPublicId, a.sessionPublicId as sessionPublicId, "
            + "a.examVersionPublicId as examVersionPublicId, a.examDefinitionPublicId as examDefinitionPublicId "
            + "from ExamAttempt a where a.tenantId = :tenantId and a.publicId in :ids and a.deleted = false")
    List<StaffContextRow> findStaffContexts(@Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

    interface StaffContextRow {
        UUID getAttemptPublicId();
        UUID getSessionPublicId();
        UUID getExamVersionPublicId();
        UUID getExamDefinitionPublicId();
    }

    Optional<ExamAttempt> findBySessionPublicIdAndStudentPublicId(UUID sessionPublicId, UUID studentPublicId);

    /** The student's latest attempt on the session. */
    Optional<ExamAttempt> findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
            UUID sessionPublicId, UUID studentPublicId);

    long countBySessionPublicIdAndStudentPublicIdAndStatus(UUID sessionPublicId, UUID studentPublicId,
            AttemptStatus status);

    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndPublicIdIn(UUID sessionPublicId, UUID tenantId,
            List<UUID> publicIds);

    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndDeletedFalse(UUID sessionPublicId, UUID tenantId);

    /** Ownership + status lookup; task content never rides on the attempt, so every student read is this light. */
    Optional<ExamAttempt> findByPublicIdAndStudentPublicId(UUID publicId, UUID studentPublicId);

    /** Serializes concurrent audio-play/answer-submit requests for the same attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ExamAttempt a WHERE a.id = :id")
    Optional<ExamAttempt> findWithLockById(@Param("id") Long id);

    /** Tenant-scoped, not student-owned — used by proctoring's force-submit (Phase 09), where the actor is a verified proctor command, not the student. */
    Optional<ExamAttempt> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** No tenant filter: reporting (Phase 10) resolves tenant from the attempt itself, then checks it against the caller — same trusted-caller pattern as session's EntitlementService. */
    Optional<ExamAttempt> findByPublicIdAndStatus(UUID publicId, AttemptStatus status);

    /** Reporting's publish fanout (Phase 10) — every submitted attempt in a session. */
    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndStatus(UUID sessionPublicId, UUID tenantId, AttemptStatus status);

    List<ExamAttempt> findByTenantIdAndSessionPublicIdInAndStudentPublicIdInAndStatus(
            UUID tenantId, List<UUID> sessionPublicIds, List<UUID> studentPublicIds, AttemptStatus status);

    /** Existence check used by support module's EntityReferenceValidator — trusted caller, never student-facing. */
    boolean existsByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** No student filter: trusted internal caller (reporting's per-attempt scoring), not a student-facing read. */
    Optional<ExamAttempt> findByPublicId(UUID publicId);

    /** Timeout hint (ms) for the heavy overview counts. */
    String OVERVIEW_QUERY_TIMEOUT_MS = "2000";

    /** Overview: submitted attempts in {@code [from, to)}; heavy count, bounded by a 2s timeout hint. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("SELECT COUNT(a) FROM ExamAttempt a WHERE a.tenantId = :tenantId AND a.deleted = false "
            + "AND a.status = com.pte.attempt.domain.enums.AttemptStatus.SUBMITTED "
            + "AND a.submittedAt >= :from AND a.submittedAt < :to")
    long countSubmittedBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** Overview: attempts currently in progress for the tenant. */
    @Query("SELECT COUNT(a) FROM ExamAttempt a WHERE a.tenantId = :tenantId AND a.deleted = false "
            + "AND a.status = com.pte.attempt.domain.enums.AttemptStatus.IN_PROGRESS")
    long countInProgress(@Param("tenantId") UUID tenantId);

    /** Overview: distinct students with an attempt started in {@code [from, to)}; heavy count with timeout hint. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("SELECT COUNT(DISTINCT a.studentPublicId) FROM ExamAttempt a WHERE a.tenantId = :tenantId "
            + "AND a.deleted = false AND a.startedAt >= :from AND a.startedAt < :to")
    long countDistinctStudentsStartedBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Overview (platform-wide): distinct students with an attempt started in {@code [from, to)} across all
     * tenants; served by the time-leading {@code started_at} index, bounded by the timeout hint.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("SELECT COUNT(DISTINCT a.studentPublicId) FROM ExamAttempt a WHERE a.deleted = false "
            + "AND a.startedAt >= :from AND a.startedAt < :to")
    long countDistinctStudentsStartedBetweenPlatformWide(@Param("from") Instant from, @Param("to") Instant to);

    /** Batch context read for report publication and grading coverage. */
    List<ExamAttempt> findAllByPublicIdInAndDeletedFalse(Collection<UUID> publicIds);

    /** The status of each given student's latest attempt in the session (students without an attempt are absent). */
    @Query("select a.studentPublicId as studentPublicId, a.status as status from ExamAttempt a "
            + "where a.sessionPublicId = :sessionPublicId and a.tenantId = :tenantId and a.deleted = false "
            + "and a.studentPublicId in :studentPublicIds and a.attemptNumber = ("
            + "select max(b.attemptNumber) from ExamAttempt b where b.sessionPublicId = a.sessionPublicId "
            + "and b.tenantId = a.tenantId and b.studentPublicId = a.studentPublicId and b.deleted = false)")
    List<LatestStatusRow> findLatestStatuses(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId, @Param("studentPublicIds") Collection<UUID> studentPublicIds);

    interface LatestStatusRow {
        UUID getStudentPublicId();

        AttemptStatus getStatus();
    }

    /** Records the latest sign of life without touching the version column, so a ping never fails a submit. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExamAttempt a set a.lastSeenAt = :seenAt where a.id = :id")
    int touchLastSeen(@Param("id") Long id, @Param("seenAt") Instant seenAt);

    /** Leaves attempts out of the session grading; a bulk UPDATE keeps the version and update time untouched. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExamAttempt a set a.gradingExcluded = true, a.gradingExclusionReason = :reason "
            + "where a.publicId = :publicId and a.tenantId = :tenantId and a.sessionPublicId = :sessionPublicId")
    int markGradingExcluded(@Param("publicId") UUID publicId, @Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("reason") String reason);

    /** Batch read of attempts for the given students on an exam session; ordered by attempt number desc. */
    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndStudentPublicIdInAndDeletedFalseOrderByAttemptNumberDesc(
            UUID sessionPublicId, UUID tenantId, Collection<UUID> studentPublicIds);

    /** Paged attempt history for a specific enrolled student on an exam session. */
    Page<ExamAttempt> findBySessionPublicIdAndStudentPublicIdAndTenantIdAndDeletedFalse(
            UUID sessionPublicId, UUID studentPublicId, UUID tenantId, Pageable pageable);

    /** Tenant- and session-scoped single attempt lookup. */
    Optional<ExamAttempt> findByPublicIdAndSessionPublicIdAndTenantId(
            UUID publicId, UUID sessionPublicId, UUID tenantId);

    /** Tenant- and session-scoped single attempt lookup under pessimistic write lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ExamAttempt a WHERE a.publicId = :publicId AND a.sessionPublicId = :sessionPublicId AND a.tenantId = :tenantId")
    Optional<ExamAttempt> findWithLockByPublicIdAndSessionPublicIdAndTenantId(
            @Param("publicId") UUID publicId, @Param("sessionPublicId") UUID sessionPublicId, @Param("tenantId") UUID tenantId);

    /** Checks whether the target attempt exists and belongs to the given exam session and tenant. */
    boolean existsByPublicIdAndSessionPublicIdAndTenantId(
            UUID publicId, UUID sessionPublicId, UUID tenantId);
}
