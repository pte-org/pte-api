package com.pte.session.internal.repository;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Optional<ExamSession> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /**
     * Pessimistic write lock on the session row — required by {@code patchPolicy()}
     * and {@code open()} so the open-vs-patch race (a host edits the policy the
     * same instant a student/host opens the session) serializes instead of
     * interleaving. Callers must be inside an active {@code @Transactional} method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId AND s.tenantId = :tenantId")
    Optional<ExamSession> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
                                                             @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.publicId = :publicId AND s.deleted = false")
    Optional<ExamSession> findWithLockByPublicId(@Param("publicId") UUID publicId);

    /** No tenant filter: used by the trusted application-call surface, not a host-scoped caller. */
    Optional<ExamSession> findByPublicId(UUID publicId);

    List<ExamSession> findByTenantId(UUID tenantId);

    @Query("SELECT s FROM ExamSession s WHERE s.deleted = false "
            + "AND s.status = com.pte.session.domain.enums.SessionStatus.OPEN "
            + "AND s.opensAt <= :now AND s.closesAt > :now AND s.closesAt <= :cutoff "
            + "ORDER BY s.closesAt ASC, s.id ASC")
    List<ExamSession> findDueClosingSoon(@Param("now") Instant now, @Param("cutoff") Instant cutoff);

    @Query("SELECT s FROM ExamSession s WHERE s.subscriptionId = :subscriptionId "
            + "AND s.status IN (com.pte.session.domain.enums.SessionStatus.SCHEDULED, "
            + "com.pte.session.domain.enums.SessionStatus.OPEN) "
            + "AND s.opensAt < :closesAt AND s.closesAt > :opensAt "
            + "AND (:excludedPublicId IS NULL OR s.publicId <> :excludedPublicId)")
    Optional<ExamSession> findFirstOverlapping(@Param("subscriptionId") UUID subscriptionId,
                                                @Param("opensAt") Instant opensAt,
                                                @Param("closesAt") Instant closesAt,
                                                @Param("excludedPublicId") UUID excludedPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ExamSession> findBySubscriptionIdAndStatus(UUID subscriptionId, SessionStatus status);

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
}
