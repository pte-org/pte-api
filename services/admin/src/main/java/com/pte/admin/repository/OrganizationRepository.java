package com.pte.admin.repository;

import com.pte.admin.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(UUID publicId);

    List<Organization> findByTenant_PublicIdOrderByCreatedAtAsc(UUID tenantPublicId);

    boolean existsByTenant_PublicIdAndNameIgnoreCase(UUID tenantPublicId, String name);
}
