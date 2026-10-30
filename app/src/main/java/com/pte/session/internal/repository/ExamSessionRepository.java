package com.pte.session.internal.repository;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ExamSession> {

    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId "
            + "AND s.tenantId = :tenantId AND s.deleted = false")
    Optional<ExamSession> findByPublicIdAndTenantId(@Param("publicId") UUID publicId,
                                                     @Param("tenantId") UUID tenantId);

    /**
     * Pessimistic write lock on the session row — required by {@code patchPolicy()}
     * and {@code open()} so the open-vs-patch race (a host edits the policy the
     * same instant a student/host opens the session) serializes instead of
     * interleaving. Callers must be inside an active {@code @Transactional} method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId "
            + "AND s.tenantId = :tenantId AND s.deleted = false")
    Optional<ExamSession> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
                                                             @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId AND s.deleted = false")
    Optional<ExamSession> findWithLockByPublicId(@Param("publicId") UUID publicId);

    /** No tenant filter: used by the trusted application-call surface, not a host-scoped caller. */
    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId AND s.deleted = false")
    Optional<ExamSession> findByPublicId(@Param("publicId") UUID publicId);

    /** Newest first — the host exam list shows non-deleted sessions in this order by default. */
    @Query("SELECT s FROM ExamSession s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "ORDER BY s.createdAt DESC, s.id DESC")
    List<ExamSession> findByTenantIdOrderByCreatedAtDescIdDesc(@Param("tenantId") UUID tenantId);

    List<ExamSession> findByTenantIdAndPublicIdInAndDeletedFalse(UUID tenantId, List<UUID> publicIds);

    @Query("SELECT s FROM ExamSession s WHERE s.deleted = false "
            + "AND s.status = com.pte.session.domain.enums.SessionStatus.OPEN "
            + "AND s.opensAt <= :now AND s.closesAt > :now AND s.closesAt <= :cutoff "
            + "ORDER BY s.closesAt ASC, s.id ASC")
    List<ExamSession> findDueClosingSoon(@Param("now") Instant now, @Param("cutoff") Instant cutoff);

    /** Overview: non-deleted sessions of the tenant grouped by lifecycle status. */
    @Query("SELECT s.status, COUNT(s) FROM ExamSession s "
            + "WHERE s.tenantId = :tenantId AND s.deleted = false GROUP BY s.status")
    List<Object[]> countByTenantGroupedByStatus(@Param("tenantId") UUID tenantId);

    /** Overview: sessions created in {@code [from, to)}. */
    @Query("SELECT COUNT(s) FROM ExamSession s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "AND s.createdAt >= :from AND s.createdAt < :to")
    long countCreatedBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** Overview: next sessions that are still joinable, earliest opening first. Callers pass a small page. */
    @Query("SELECT s.id AS id, s.publicId AS publicId, s.name AS name, s.status AS status, "
            + "s.opensAt AS opensAt, s.closesAt AS closesAt, s.capacity AS capacity "
            + "FROM ExamSession s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "AND s.status IN :statuses AND s.closesAt > :now ORDER BY s.opensAt ASC, s.id ASC")
    List<UpcomingSessionRow> findUpcoming(@Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<SessionStatus> statuses, @Param("now") Instant now, Pageable pageable);

    /** Projection for {@link #findUpcoming}; no entity is materialised. */
    interface UpcomingSessionRow {
        Long getId();
        UUID getPublicId();
        String getName();
        SessionStatus getStatus();
        Instant getOpensAt();
        Instant getClosesAt();
        Integer getCapacity();
    }

    /** Global check (deleted rows included)— codes are unique across all tenants. */
    boolean existsBySessionCode(String sessionCode);

    /**
     * Student code lookup. Unlike the other session queries this one filters
     * {@code deleted}: a deleted session whose enrollments survived must not resolve.
     */
    Optional<ExamSession> findBySessionCodeAndTenantIdAndDeletedFalse(String sessionCode, UUID tenantId);

    @Query("SELECT s FROM ExamSession s WHERE s.subscriptionId = :subscriptionId "
            + "AND s.deleted = false "
            + "AND s.status IN (com.pte.session.domain.enums.SessionStatus.SCHEDULED, "
            + "com.pte.session.domain.enums.SessionStatus.OPEN) "
            + "AND s.opensAt < :closesAt AND s.closesAt > :opensAt "
            + "AND (:excludedPublicId IS NULL OR s.publicId <> :excludedPublicId)")
    Optional<ExamSession> findFirstOverlapping(@Param("subscriptionId") UUID subscriptionId,
                                                @Param("opensAt") Instant opensAt,
                                                @Param("closesAt") Instant closesAt,
                                                @Param("excludedPublicId") UUID excludedPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.subscriptionId = :subscriptionId "
            + "AND s.status = :status AND s.deleted = false")
    List<ExamSession> findBySubscriptionIdAndStatus(@Param("subscriptionId") UUID subscriptionId,
                                                     @Param("status") SessionStatus status);

    @Query("SELECT s.publicId FROM ExamSession s WHERE s.deleted = false "
            + "AND s.status = com.pte.session.domain.enums.SessionStatus.OPEN "
            + "AND s.closesAt <= :now ORDER BY s.closesAt ASC, s.id ASC")
    List<UUID> findDueForAutoClose(@Param("now") Instant now);

    @Query("SELECT s.publicId FROM ExamSession s WHERE s.deleted = false "
            + "AND s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED "
            + "AND s.opensAt <= :now "
            + "ORDER BY s.opensAt ASC, s.id ASC")
    List<UUID> findDueForAutoOpen(@Param("now") Instant now);

    /**
     * Locks every non-deleted session in public-id order. Billing holds the
     * subscription lock before invoking this query, so revoke and session
     * writers use one deterministic hierarchy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.subscriptionId = :subscriptionId "
            + "AND s.deleted = false ORDER BY s.publicId ASC")
    List<ExamSession> findWithLockBySubscriptionIdOrderByPublicIdAsc(
            @Param("subscriptionId") UUID subscriptionId);

    /** Sessions whose grading is frozen and not yet complete, across tenants, for the reconciliation sweep. */
    @Query("SELECT s FROM ExamSession s WHERE s.deleted = false "
            + "AND s.gradingStatus = com.pte.session.domain.enums.SessionGradingStatus.FROZEN")
    List<ExamSession> findFrozenGrading();

    @Query("SELECT COUNT(s) FROM ExamSession s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "AND s.gradingStatus = :status")
    long countByTenantAndGradingStatus(@Param("tenantId") UUID tenantId,
            @Param("status") com.pte.session.domain.enums.SessionGradingStatus status);

    @Query("SELECT s FROM ExamSession s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "AND s.publicId IN :publicIds")
    List<ExamSession> findByTenantAndPublicIds(@Param("tenantId") UUID tenantId,
            @Param("publicIds") java.util.Collection<UUID> publicIds);
}
