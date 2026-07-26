package com.pte.admin.repository;

import com.pte.admin.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByPublicId(UUID publicId);

    boolean existsByName(String name);
}
