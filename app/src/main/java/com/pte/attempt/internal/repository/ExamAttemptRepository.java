package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long>,
        JpaSpecificationExecutor<ExamAttempt> {

    @Query("select a.publicId as attemptPublicId, a.sessionPublicId as sessionPublicId, "
            + "p.publicId as pinnedPublicId, p.scoreTemplatePublicId as scoreTemplatePublicId, "
            + "p.tenantId as pinnedTenantId, p.sourceSessionPublicId as pinnedSessionPublicId "
            + "from ExamAttempt a left join a.pinnedSnapshot p on p.deleted = false "
            + "where a.tenantId = :tenantId and a.publicId in :ids and a.deleted = false")
    List<StaffContextRow> findStaffContexts(@Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

    @Query("select a.publicId as attemptPublicId, coalesce(i.taskTypeKey, i.taskType) as taskType, "
            + "count(i.id) as taskCount from ExamAttempt a join a.pinnedSnapshot p join p.items i "
            + "where a.tenantId = :tenantId and a.publicId in :ids and a.deleted = false "
            + "and p.deleted = false and i.deleted = false and p.tenantId = :tenantId "
            + "and p.sourceSessionPublicId = a.sessionPublicId "
            + "group by a.publicId, coalesce(i.taskTypeKey, i.taskType)")
    List<StaffTaskCountRow> countStaffPinnedTasks(@Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

    interface StaffContextRow {
        UUID getAttemptPublicId(); UUID getSessionPublicId(); UUID getPinnedPublicId();
        UUID getScoreTemplatePublicId(); UUID getPinnedTenantId(); UUID getPinnedSessionPublicId();
    }
    interface StaffTaskCountRow { UUID getAttemptPublicId(); String getTaskType(); long getTaskCount(); }

    Optional<ExamAttempt> findBySessionPublicIdAndStudentPublicId(UUID sessionPublicId, UUID studentPublicId);

    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    Optional<ExamAttempt> findWithPinnedBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
            UUID sessionPublicId, UUID studentPublicId);

    /** Compatibility alias; production lifecycle uses the ordered multi-attempt query above. */
    default Optional<ExamAttempt> findWithPinnedBySessionPublicIdAndStudentPublicId(UUID sessionPublicId,
            UUID studentPublicId) {
        return findWithPinnedBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                sessionPublicId, studentPublicId);
    }

    long countBySessionPublicIdAndStudentPublicIdAndStatus(UUID sessionPublicId, UUID studentPublicId,
            AttemptStatus status);

    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndPublicIdIn(UUID sessionPublicId, UUID tenantId,
            List<UUID> publicIds);

    List<ExamAttempt> findBySessionPublicIdAndTenantIdAndDeletedFalse(UUID sessionPublicId, UUID tenantId);

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

    List<ExamAttempt> findByTenantIdAndSessionPublicIdInAndStudentPublicIdInAndStatus(
            UUID tenantId, List<UUID> sessionPublicIds, List<UUID> studentPublicIds, AttemptStatus status);

    /** Existence check used by support module's EntityReferenceValidator — trusted caller, never student-facing. */
    boolean existsByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** Mirrors {@link #findWithPinnedByPublicIdAndStudentPublicId} but no student filter — trusted internal caller (reporting's per-attempt scoring, Phase 5), not a student-facing read. */
    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    Optional<ExamAttempt> findWithPinnedByPublicId(UUID publicId);

    /** Overview: submitted attempts in {@code [from, to)}; heavy count, bounded by a 2s timeout hint. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
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
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("SELECT COUNT(DISTINCT a.studentPublicId) FROM ExamAttempt a WHERE a.tenantId = :tenantId "
            + "AND a.deleted = false AND a.startedAt >= :from AND a.startedAt < :to")
    long countDistinctStudentsStartedBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** Batch context read for report publication; loads each pinned section set with a single query. */
    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    @Query("SELECT DISTINCT a FROM ExamAttempt a WHERE a.publicId IN :publicIds AND a.deleted = false")
    List<ExamAttempt> findAllWithPinnedByPublicIds(@Param("publicIds") List<UUID> publicIds);
}
