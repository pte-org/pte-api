package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamIncidentRepository extends JpaRepository<ExamIncident, Long> {

    Page<ExamIncident> findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalse(
            UUID sessionPublicId, UUID tenantId, IncidentOrigin origin, Pageable pageable);

    Page<ExamIncident> findBySessionPublicIdAndTenantIdAndOriginAndStatusAndDeletedFalse(
            UUID sessionPublicId, UUID tenantId, IncidentOrigin origin, String status, Pageable pageable);

    Optional<ExamIncident> findByPublicIdAndSessionPublicIdAndTenantIdAndOriginAndDeletedFalse(
            UUID publicId, UUID sessionPublicId, UUID tenantId, IncidentOrigin origin);

    /** Includes soft-deleted rows: the unique constraint on (attempt, client event) spans them too. */
    Optional<ExamIncident> findByAttemptPublicIdAndClientEventId(UUID attemptPublicId, String clientEventId);

    List<ExamIncident> findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
            UUID sessionPublicId, UUID tenantId, IncidentOrigin origin);

    /** First page of the security audit. A separate query from {@link #findAuditAfter}: Postgres cannot type a null cursor. */
    @Query("""
            SELECT incident FROM ExamIncident incident
            WHERE incident.sessionPublicId = :sessionPublicId
              AND incident.tenantId = :tenantId
              AND incident.origin IN :origins
              AND incident.deleted = false
            ORDER BY incident.detectedAt ASC, incident.publicId ASC
            """)
    List<ExamIncident> findAuditFirstPage(@Param("sessionPublicId") UUID sessionPublicId,
                                          @Param("tenantId") UUID tenantId,
                                          @Param("origins") Collection<IncidentOrigin> origins,
                                          Pageable pageable);

    /** The page after a keyset cursor of (detectedAt, publicId). */
    @Query("""
            SELECT incident FROM ExamIncident incident
            WHERE incident.sessionPublicId = :sessionPublicId
              AND incident.tenantId = :tenantId
              AND incident.origin IN :origins
              AND incident.deleted = false
              AND (incident.detectedAt > :cursorAt
                   OR (incident.detectedAt = :cursorAt AND incident.publicId > :cursorId))
            ORDER BY incident.detectedAt ASC, incident.publicId ASC
            """)
    List<ExamIncident> findAuditAfter(@Param("sessionPublicId") UUID sessionPublicId,
                                      @Param("tenantId") UUID tenantId,
                                      @Param("origins") Collection<IncidentOrigin> origins,
                                      @Param("cursorAt") Instant cursorAt,
                                      @Param("cursorId") UUID cursorId,
                                      Pageable pageable);

    @Query("""
            SELECT incident.attemptPublicId, COUNT(incident) FROM ExamIncident incident
            WHERE incident.sessionPublicId = :sessionPublicId
              AND incident.tenantId = :tenantId
              AND incident.origin = com.pte.proctoring.domain.enums.IncidentOrigin.PROCTOR_FLAG
              AND incident.attemptPublicId IN :attemptPublicIds
              AND incident.deleted = false
            GROUP BY incident.attemptPublicId
            """)
    List<Object[]> countFlagsByAttemptPublicIdIn(@Param("sessionPublicId") UUID sessionPublicId,
                                                 @Param("tenantId") UUID tenantId,
                                                 @Param("attemptPublicIds") Collection<UUID> attemptPublicIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ExamIncident incident
            SET incident.deleted = true
            WHERE incident.deleted = false
              AND incident.origin = com.pte.proctoring.domain.enums.IncidentOrigin.AUTO
              AND incident.detectedAt < :cutoff
            """)
    int markExpiredEvidenceBefore(@Param("cutoff") Instant cutoff);
}
