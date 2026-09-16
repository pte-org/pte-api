package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.publicId = :publicId")
    Optional<Tenant> findWithLockByPublicId(@Param("publicId") UUID publicId);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    boolean existsByPublicId(UUID publicId);

    @Query("select t.organizationType from Tenant t where t.publicId = :tenantId")
    Optional<String> findOrganizationTypeByPublicId(@Param("tenantId") UUID tenantId);
}
