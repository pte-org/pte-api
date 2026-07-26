package com.pte.iam.repository;

import com.pte.iam.domain.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRegistryRepository extends JpaRepository<TenantRegistry, Long> {

    Optional<TenantRegistry> findByTenantPublicId(UUID tenantPublicId);
}
