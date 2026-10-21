package com.pte.tenancy.internal.repository;

import com.pte.tenancy.TenantSeatLimit;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByPublicId(UUID publicId);

    List<Tenant> findByPublicIdIn(Collection<UUID> publicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.publicId = :publicId")
    Optional<Tenant> findWithLockByPublicId(@Param("publicId") UUID publicId);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);

    boolean existsByPublicId(UUID publicId);

    @Query("select t.organizationType from Tenant t where t.publicId = :tenantId")
    Optional<String> findOrganizationTypeByPublicId(@Param("tenantId") UUID tenantId);

    List<Tenant> findByPublicIdInAndStatusAndDeletedFalse(Collection<UUID> publicIds, TenantStatus status);

    List<Tenant> findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
            TenantStatus status, Instant now);

    /** Overview: non-deleted tenants grouped by status (counts only). */
    @Query("select t.status, count(t) from Tenant t where t.deleted = false group by t.status")
    List<Object[]> countGroupedByStatus();

    /** Overview: non-deleted tenants grouped by organization type (counts only). */
    @Query("select t.organizationType, count(t) from Tenant t where t.deleted = false group by t.organizationType")
    List<Object[]> countGroupedByOrganizationType();

    /** Overview: tenants created in {@code [from, to)}. */
    @Query("select count(t) from Tenant t where t.deleted = false and t.createdAt >= :from and t.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview: newest non-deleted tenants, bounded by {@code pageable}. */
    @Query("select t from Tenant t where t.deleted = false")
    List<Tenant> findRecent(Pageable pageable);

    /** Overview: seat limit of every non-deleted tenant in one projection query (no entity load). */
    @Query("select new com.pte.tenancy.TenantSeatLimit(t.publicId, t.code, t.name, t.studentLimit) "
            + "from Tenant t where t.deleted = false")
    List<TenantSeatLimit> findAllSeatLimits();
}
