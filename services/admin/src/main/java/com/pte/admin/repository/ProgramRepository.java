package com.pte.admin.repository;

import com.pte.admin.domain.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByPublicId(UUID publicId);

    List<Program> findByOrganization_PublicIdAndDeletedFalseOrderByCreatedAtAsc(UUID organizationPublicId);

    /** Single-join, tenant-wide — not called until Phase 13's dashboard, added now while touching this file. */
    List<Program> findByOrganization_Tenant_PublicIdAndDeletedFalse(UUID tenantPublicId);

    boolean existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(UUID organizationPublicId, String name);
}
