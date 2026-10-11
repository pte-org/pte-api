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
