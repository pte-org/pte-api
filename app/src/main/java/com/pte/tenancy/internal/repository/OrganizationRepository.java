package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.publicId = :publicId")
    Optional<Organization> findWithLockByPublicId(@Param("publicId") UUID publicId);

    List<Organization> findByTenant_PublicIdOrderByCreatedAtAsc(UUID tenantPublicId);

    /** Overview: non-deleted organizations (branches) of one tenant. */
    long countByTenant_PublicIdAndDeletedFalse(UUID tenantPublicId);
}
