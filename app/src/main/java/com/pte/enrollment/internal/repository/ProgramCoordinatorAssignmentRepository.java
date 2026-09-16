package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ProgramCoordinatorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramCoordinatorAssignmentRepository extends JpaRepository<ProgramCoordinatorAssignment, Long> {

    Optional<ProgramCoordinatorAssignment> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    List<ProgramCoordinatorAssignment> findByTenantIdAndProgram_PublicId(UUID tenantId, UUID programPublicId);
}
