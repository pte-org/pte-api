package com.pte.session.internal.repository;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    @EntityGraph(attributePaths = "composition")
    Optional<ExamSession> findWithCompositionByPublicIdAndTenantId(UUID publicId, UUID tenantId);

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

    /** No tenant filter: used by the trusted application-call surface, not a host-scoped caller. */
    @EntityGraph(attributePaths = "composition")
    Optional<ExamSession> findWithCompositionByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "composition")
    List<ExamSession> findByTenantId(UUID tenantId);

    @Query("SELECT s FROM ExamSession s WHERE s.subscriptionId = :subscriptionId "
            + "AND s.opensAt < :closesAt AND s.closesAt > :opensAt "
            + "AND (:excludedPublicId IS NULL OR s.publicId <> :excludedPublicId)")
    Optional<ExamSession> findFirstOverlapping(@Param("subscriptionId") UUID subscriptionId,
                                                @Param("opensAt") Instant opensAt,
                                                @Param("closesAt") Instant closesAt,
                                                @Param("excludedPublicId") UUID excludedPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ExamSession> findBySubscriptionIdAndStatus(UUID subscriptionId, SessionStatus status);
}
