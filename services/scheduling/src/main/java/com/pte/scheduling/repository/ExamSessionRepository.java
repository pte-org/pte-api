package com.pte.scheduling.repository;

import com.pte.scheduling.domain.ExamSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** No tenant filter: used by the internal service surface, not a host-scoped caller. */
    @EntityGraph(attributePaths = "composition")
    Optional<ExamSession> findWithCompositionByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "composition")
    List<ExamSession> findByTenantId(UUID tenantId);
}
