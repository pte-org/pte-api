package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.LecturerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LecturerAssignmentRepository extends JpaRepository<LecturerAssignment, Long> {

    Optional<LecturerAssignment> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    List<LecturerAssignment> findByTenantIdAndStudentClass_PublicId(UUID tenantId, UUID classPublicId);
}
