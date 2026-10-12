package com.pte.session.internal.repository;

import com.pte.session.domain.ProctorAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProctorAssignmentRepository extends JpaRepository<ProctorAssignment, Long> {

    List<ProctorAssignment> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndProctorPublicId(Long sessionId, UUID proctorPublicId);

    Optional<ProctorAssignment> findByPublicId(UUID publicId);

    @Query(value = "SELECT pa FROM ProctorAssignment pa JOIN FETCH pa.session s "
            + "WHERE pa.proctorPublicId = :proctorPublicId AND pa.tenantId = :tenantId "
            + "AND pa.deleted = false AND s.deleted = false",
            countQuery = "SELECT count(pa) FROM ProctorAssignment pa JOIN pa.session s "
            + "WHERE pa.proctorPublicId = :proctorPublicId AND pa.tenantId = :tenantId "
            + "AND pa.deleted = false AND s.deleted = false")
    Page<ProctorAssignment> findAssignedSessions(
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    @Query(value = """
            SELECT
                COUNT(CASE WHEN s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                                AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                                AND s.opensAt <= :asOf AND :asOf < s.closesAt THEN 1 END) AS accessible,
                COUNT(CASE WHEN (s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED
                                 OR s.status = com.pte.session.domain.enums.SessionStatus.OPEN)
                                AND s.opensAt IS NOT NULL AND s.opensAt > :asOf THEN 1 END) AS upcoming,
                COUNT(CASE WHEN s.status <> com.pte.session.domain.enums.SessionStatus.CANCELLED
                                AND s.opensAt IS NOT NULL AND s.opensAt >= :dayStart AND s.opensAt < :dayEnd THEN 1 END) AS today
            FROM ProctorAssignment pa
            JOIN pa.session s
            WHERE pa.proctorPublicId = :proctorPublicId
              AND pa.tenantId = :tenantId
              AND pa.deleted = false
              AND s.deleted = false
            """)
    ProctorOverviewCountsProjection countWorkspaceOverview(
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId,
            @Param("asOf") java.time.Instant asOf,
            @Param("dayStart") java.time.Instant dayStart,
            @Param("dayEnd") java.time.Instant dayEnd);

    @Query(value = """
            SELECT pa FROM ProctorAssignment pa
            JOIN FETCH pa.session s
            WHERE pa.proctorPublicId = :proctorPublicId
              AND pa.tenantId = :tenantId
              AND pa.deleted = false
              AND s.deleted = false
              AND (
                  (s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                   AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                   AND s.opensAt <= :asOf AND :asOf < s.closesAt)
                  OR
                  ((s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED
                    OR s.status = com.pte.session.domain.enums.SessionStatus.OPEN)
                   AND s.opensAt IS NOT NULL AND s.opensAt > :asOf)
              )
            ORDER BY
                CASE
                    WHEN s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                         AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                         AND s.opensAt <= :asOf AND :asOf < s.closesAt THEN 0
                    ELSE 1
                END ASC,
                s.opensAt ASC NULLS LAST,
                s.publicId ASC,
                pa.publicId ASC
            """)
    List<ProctorAssignment> findPrioritySessions(
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId,
            @Param("asOf") java.time.Instant asOf,
            Pageable pageable);

    @Query(value = """
            SELECT pa FROM ProctorAssignment pa
            JOIN FETCH pa.session s
            WHERE pa.proctorPublicId = :proctorPublicId
              AND pa.tenantId = :tenantId
              AND pa.deleted = false
              AND s.deleted = false
              AND (
                  :scope = 'ALL'
                  OR (:scope = 'ACCESSIBLE'
                      AND s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                      AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                      AND s.opensAt <= :asOf AND :asOf < s.closesAt)
                  OR (:scope = 'UPCOMING'
                      AND (s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED
                           OR s.status = com.pte.session.domain.enums.SessionStatus.OPEN)
                      AND s.opensAt IS NOT NULL AND s.opensAt > :asOf)
                  OR (:scope = 'TODAY'
                      AND s.status <> com.pte.session.domain.enums.SessionStatus.CANCELLED
                      AND s.opensAt IS NOT NULL AND s.opensAt >= :dayStart AND s.opensAt < :dayEnd)
              )
            ORDER BY
                CASE WHEN :usePrioritySort = true THEN
                    CASE
                        WHEN s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                             AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                             AND s.opensAt <= :asOf AND :asOf < s.closesAt THEN 0
                        WHEN (s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED
                              OR s.status = com.pte.session.domain.enums.SessionStatus.OPEN)
                             AND s.opensAt IS NOT NULL AND s.opensAt > :asOf THEN 1
                        ELSE 2
                    END
                ELSE 0 END ASC,
                s.opensAt ASC NULLS LAST,
                s.publicId ASC,
                pa.publicId ASC
            """,
            countQuery = """
            SELECT count(pa) FROM ProctorAssignment pa
            JOIN pa.session s
            WHERE pa.proctorPublicId = :proctorPublicId
              AND pa.tenantId = :tenantId
              AND pa.deleted = false
              AND s.deleted = false
              AND (
                  :scope = 'ALL'
                  OR (:scope = 'ACCESSIBLE'
                      AND s.status = com.pte.session.domain.enums.SessionStatus.OPEN
                      AND s.opensAt IS NOT NULL AND s.closesAt IS NOT NULL
                      AND s.opensAt <= :asOf AND :asOf < s.closesAt)
                  OR (:scope = 'UPCOMING'
                      AND (s.status = com.pte.session.domain.enums.SessionStatus.SCHEDULED
                           OR s.status = com.pte.session.domain.enums.SessionStatus.OPEN)
                      AND s.opensAt IS NOT NULL AND s.opensAt > :asOf)
                  OR (:scope = 'TODAY'
                      AND s.status <> com.pte.session.domain.enums.SessionStatus.CANCELLED
                      AND s.opensAt IS NOT NULL AND s.opensAt >= :dayStart AND s.opensAt < :dayEnd)
              )
            """)
    Page<ProctorAssignment> findScopedAssignedSessions(
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId,
            @Param("scope") String scope,
            @Param("asOf") java.time.Instant asOf,
            @Param("dayStart") java.time.Instant dayStart,
            @Param("dayEnd") java.time.Instant dayEnd,
            @Param("usePrioritySort") boolean usePrioritySort,
            Pageable pageable);

    @Query("SELECT pa FROM ProctorAssignment pa JOIN FETCH pa.session s "
            + "WHERE s.publicId = :sessionPublicId AND pa.proctorPublicId = :proctorPublicId "
            + "AND pa.tenantId = :tenantId AND pa.deleted = false AND s.deleted = false")
    Optional<ProctorAssignment> findAssignment(
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(pa) > 0 THEN true ELSE false END FROM ProctorAssignment pa "
            + "WHERE pa.session.publicId = :sessionPublicId AND pa.proctorPublicId = :proctorPublicId "
            + "AND pa.tenantId = :tenantId AND pa.deleted = false AND pa.session.deleted = false")
    boolean isProctorAssigned(
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId);
}
