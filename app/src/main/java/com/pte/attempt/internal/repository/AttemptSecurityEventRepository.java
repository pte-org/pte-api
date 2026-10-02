package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.AttemptSecurityEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptSecurityEventRepository extends JpaRepository<AttemptSecurityEvent, Long> {

    Optional<AttemptSecurityEvent> findByAttemptIdAndClientEventId(Long attemptId, String clientEventId);

    @Query("""
            SELECT event FROM AttemptSecurityEvent event
            WHERE event.sessionPublicId = :sessionPublicId
              AND event.tenantId = :tenantId
              AND event.deleted = false
              AND (:cursorAt IS NULL
                   OR event.detectedAt > :cursorAt
                   OR (event.detectedAt = :cursorAt AND event.publicId > :cursorId))
            ORDER BY event.detectedAt ASC, event.publicId ASC
            """)
    List<AttemptSecurityEvent> findForSession(@Param("sessionPublicId") UUID sessionPublicId,
                                               @Param("tenantId") UUID tenantId,
                                               @Param("cursorAt") Instant cursorAt,
                                               @Param("cursorId") UUID cursorId,
                                               Pageable pageable);

    @Modifying
    @Query("""
            UPDATE AttemptSecurityEvent event
            SET event.deleted = true
            WHERE event.deleted = false
              AND event.detectedAt < :cutoff
            """)
    int markExpiredBefore(@Param("cutoff") Instant cutoff);
}
