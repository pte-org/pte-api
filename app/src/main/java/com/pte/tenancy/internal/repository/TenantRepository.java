package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByPublicId(UUID publicId);

    boolean existsByName(String name);

    boolean existsByPublicId(UUID publicId);

    @Query("select t.organizationType from Tenant t where t.publicId = :tenantId")
    Optional<String> findOrganizationTypeByPublicId(@Param("tenantId") UUID tenantId);
}
