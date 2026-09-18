package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(UUID publicId);

    List<Organization> findByTenant_PublicIdOrderByCreatedAtAsc(UUID tenantPublicId);
}
