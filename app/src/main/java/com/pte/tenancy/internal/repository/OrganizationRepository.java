package com.pte.tenancy.internal.repository;

import com.pte.tenancy.TenantSeatLimit;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
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

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(UUID publicId);

    List<Organization> findByPublicIdIn(Collection<UUID> publicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.publicId = :publicId")
    Optional<Organization> findWithLockByPublicId(@Param("publicId") UUID publicId);

    boolean existsByPublicId(UUID publicId);

    boolean existsByCodeAndStatusNot(String code, OrganizationStatus status);

    boolean existsByNameAndStatusNot(String name, OrganizationStatus status);

    boolean existsByTaxCodeAndStatusNot(String taxCode, OrganizationStatus status);

    boolean existsByTaxCodeAndStatusInAndPublicIdNot(String taxCode, Collection<OrganizationStatus> statuses,
            UUID excludedPublicId);

    @Query("select o.organizationType from Organization o where o.publicId = :organizationId")
    Optional<String> findOrganizationTypeByPublicId(@Param("organizationId") UUID organizationId);

    List<Organization> findByPublicIdInAndStatusAndDeletedFalse(Collection<UUID> publicIds, OrganizationStatus status);

    List<Organization> findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
            OrganizationStatus status, Instant now);

    /** Approved organizations (tenants), newest first; applications still under review are excluded. */
    @Query("select o from Organization o where o.status in (com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED) order by o.createdAt desc")
    List<Organization> findAllApproved();

    /**
     * Platform lookup of approved organizations by code or tax-code prefix, or exact contact email, all
     * case-insensitive; {@code prefix} already ends with {@code %}.
     */
    @Query("select o from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED) and ("
            + "lower(o.code) like :prefix or lower(o.taxCode) like :prefix or lower(o.contactEmail) = :exact) "
            + "order by o.name asc")
    List<Organization> searchApproved(@Param("prefix") String prefix, @Param("exact") String exact,
            Pageable pageable);

    /** Overview: non-deleted approved organizations grouped by status (counts only). */
    @Query("select o.status, count(o) from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED) group by o.status")
    List<Object[]> countGroupedByStatus();

    /** Overview: non-deleted approved organizations grouped by organization type (counts only). */
    @Query("select o.organizationType, count(o) from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED) group by o.organizationType")
    List<Object[]> countGroupedByOrganizationType();

    /** Overview: approved organizations created in {@code [from, to)}. */
    @Query("select count(o) from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED) "
            + "and o.createdAt >= :from and o.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview: newest non-deleted approved organizations, bounded by {@code pageable}. */
    @Query("select o from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED)")
    List<Organization> findRecent(Pageable pageable);

    /** Overview: seat limit of every non-deleted approved organization in one projection query. */
    @Query("select new com.pte.tenancy.TenantSeatLimit(o.publicId, o.code, o.name, o.studentLimit) "
            + "from Organization o where o.deleted = false and o.status in "
            + "(com.pte.tenancy.domain.enums.OrganizationStatus.ACTIVE, "
            + "com.pte.tenancy.domain.enums.OrganizationStatus.SUSPENDED)")
    List<TenantSeatLimit> findAllSeatLimits();

    // ---- onboarding applications (organizations that were submitted through the public form) ----

    @Query("select o from Organization o where o.contactEmail is not null order by o.createdAt desc")
    List<Organization> findAllApplications();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.publicId = :publicId and o.contactEmail is not null")
    Optional<Organization> findApplicationForUpdate(@Param("publicId") UUID publicId);

    @Query("select o from Organization o where o.publicId = :publicId and o.contactEmail is not null")
    Optional<Organization> findApplication(@Param("publicId") UUID publicId);

    /** Overview: applications in the given review state (approved ones are ACTIVE or SUSPENDED organizations). */
    @Query("select count(o) from Organization o where o.deleted = false and o.contactEmail is not null "
            + "and o.status = :status")
    long countApplicationsByStatus(@Param("status") OrganizationStatus status);

    /** Overview: applications received (created) in {@code [from, to)}. */
    @Query("select count(o) from Organization o where o.deleted = false and o.contactEmail is not null "
            + "and o.createdAt >= :from and o.createdAt < :to")
    long countApplicationsReceivedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview: oldest applications in the given state first (waiting-time view), bounded by {@code pageable}. */
    @Query("select o from Organization o where o.deleted = false and o.contactEmail is not null "
            + "and o.status = :status order by o.createdAt asc, o.id asc")
    List<Organization> findOldestApplicationsByStatus(@Param("status") OrganizationStatus status, Pageable pageable);
}
