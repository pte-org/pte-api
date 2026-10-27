package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.enums.ProgramStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByPublicId(UUID publicId);

    List<Program> findByOrganization_PublicIdAndDeletedFalseOrderByCreatedAtAsc(UUID organizationPublicId);

    /** Single-join, tenant-wide â€” not called until Phase 13's dashboard, added now while touching this file. */
    List<Program> findByOrganization_PublicIdAndDeletedFalse(UUID tenantPublicId);

    /** Overview: count of non-deleted programs of one tenant in the given status. */
    long countByOrganization_PublicIdAndStatusAndDeletedFalse(UUID tenantPublicId, ProgramStatus status);

    boolean existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(UUID organizationPublicId, String name);
}
