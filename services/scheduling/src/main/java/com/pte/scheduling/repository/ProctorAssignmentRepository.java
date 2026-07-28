package com.pte.scheduling.repository;

import com.pte.scheduling.domain.ProctorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProctorAssignmentRepository extends JpaRepository<ProctorAssignment, Long> {

    List<ProctorAssignment> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndProctorPublicId(Long sessionId, UUID proctorPublicId);

    @Query("""
            select assignment
            from ProctorAssignment assignment
            join fetch assignment.session session
            where assignment.proctorPublicId = :proctorPublicId
              and assignment.tenantId = :tenantId
            order by session.opensAt asc, session.publicId asc
            """)
    List<ProctorAssignment> findAssignedSessions(
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId);
}
