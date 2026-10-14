package com.pte.session.internal.repository;

import com.pte.session.domain.ProctorAssignment;
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

    @Query("select p.proctorPublicId from ProctorAssignment p "
            + "where p.session.publicId = :sessionPublicId and p.tenantId = :tenantId "
            + "and p.deleted = false and p.session.deleted = false "
            + "order by p.proctorPublicId")
    List<UUID> findAssignedProctorPublicIds(@Param("sessionPublicId") UUID sessionPublicId,
            @Param("tenantId") UUID tenantId);
}
