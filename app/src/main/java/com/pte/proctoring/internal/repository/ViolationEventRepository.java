package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ViolationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    List<ViolationEvent> findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(UUID sessionPublicId, UUID tenantId);

    @Query("""
            SELECT event FROM ViolationEvent event
            WHERE event.sessionPublicId = :sessionPublicId
              AND event.tenantId = :tenantId
              AND event.deleted = false
              AND (:cursorAt IS NULL
                   OR event.detectedAt > :cursorAt
                   OR (event.detectedAt = :cursorAt AND event.publicId > :cursorId))
            ORDER BY event.detectedAt ASC, event.publicId ASC
            """)
    List<ViolationEvent> findForSecurityAudit(@Param("sessionPublicId") UUID sessionPublicId,
                                               @Param("tenantId") UUID tenantId,
                                               @Param("cursorAt") Instant cursorAt,
                                               @Param("cursorId") UUID cursorId,
                                               Pageable pageable);
}
