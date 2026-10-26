package com.pte.identity.internal.repository;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped reads and counts of accounts. Every method filters on {@code organizationId}; a member whose
 * membership or account was removed is never returned.
 */
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    /** Timeout hint (ms) for the platform-wide overview count. */
    String OVERVIEW_QUERY_TIMEOUT_MS = "2000";

    List<OrganizationMember> findByUser_IdAndDeletedFalse(Long userId);

    Optional<OrganizationMember> findByUser_IdAndOrganizationIdAndDeletedFalse(Long userId, UUID organizationId);

    @Query("select m from OrganizationMember m join fetch m.user u where u.publicId = :userPublicId "
            + "and m.organizationId = :organizationId and m.deleted = false and u.deleted = false")
    Optional<OrganizationMember> findActiveByUserAndOrganization(@Param("userPublicId") UUID userPublicId,
            @Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OrganizationMember m join fetch m.user u where u.publicId = :userPublicId "
            + "and m.organizationId = :organizationId and m.deleted = false and u.deleted = false")
    Optional<OrganizationMember> findWithLockByUserAndOrganization(@Param("userPublicId") UUID userPublicId,
            @Param("organizationId") UUID organizationId);

    @Query("select m from OrganizationMember m join fetch m.user u where u.publicId in :userPublicIds "
            + "and m.organizationId = :organizationId and m.deleted = false and u.deleted = false")
    List<OrganizationMember> findActiveByUsersAndOrganization(@Param("userPublicIds") Collection<UUID> userPublicIds,
            @Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OrganizationMember m join fetch m.user u where u.publicId in :userPublicIds "
            + "and m.organizationId = :organizationId and m.deleted = false and u.deleted = false "
            + "order by u.publicId")
    List<OrganizationMember> findWithLockByUsersAndOrganization(
            @Param("userPublicIds") Collection<UUID> userPublicIds, @Param("organizationId") UUID organizationId);

    /** Every active member of an organization. */
    @Query("select m from OrganizationMember m join fetch m.user u where m.organizationId = :organizationId "
            + "and m.deleted = false and u.deleted = false")
    List<OrganizationMember> findActiveByOrganization(@Param("organizationId") UUID organizationId);

    @Query("select m from OrganizationMember m join fetch m.user u where m.organizationId = :organizationId "
            + "and m.deleted = false and u.deleted = false and lower(trim(u.email)) in :emails")
    List<OrganizationMember> findActiveByOrganizationAndEmails(@Param("organizationId") UUID organizationId,
            @Param("emails") Collection<String> lowerCaseEmails);

    /** Whether another active member of the organization already uses this e-mail (case-insensitive). */
    @Query("select (count(m) > 0) from OrganizationMember m join m.user u where m.organizationId = :organizationId "
            + "and m.deleted = false and u.deleted = false and lower(trim(u.email)) = :email "
            + "and u.publicId <> :excludedUserPublicId")
    boolean existsCanonicalEmail(@Param("organizationId") UUID organizationId, @Param("email") String email,
            @Param("excludedUserPublicId") UUID excludedUserPublicId);

    @Query("select count(distinct m.id) from OrganizationMember m join m.user u join m.roles r "
            + "where m.organizationId = :organizationId and r = :role and m.deleted = false and u.deleted = false")
    long countByOrganizationIdAndRole(@Param("organizationId") UUID organizationId, @Param("role") Role role);

    /** Overview: grouped learner counts by account status. */
    @Query("select m.status, count(distinct m.id) from OrganizationMember m join m.user u join m.roles r "
            + "where m.organizationId = :organizationId and r = :role and m.deleted = false and u.deleted = false "
            + "group by m.status")
    List<Object[]> countByOrganizationAndRoleGroupedByStatus(@Param("organizationId") UUID organizationId,
            @Param("role") Role role);

    /** Overview: new members with the role created in {@code [from, to)}. */
    @Query("select count(distinct m.id) from OrganizationMember m join m.user u join m.roles r "
            + "where m.organizationId = :organizationId and r = :role and m.deleted = false and u.deleted = false "
            + "and m.createdAt >= :from and m.createdAt < :to")
    long countByOrganizationAndRoleCreatedBetween(@Param("organizationId") UUID organizationId,
            @Param("role") Role role, @Param("from") Instant from, @Param("to") Instant to);

    /** Overview: active members per role for a small role set (staff coverage). */
    @Query("select r, count(distinct m.id) from OrganizationMember m join m.user u join m.roles r "
            + "where m.organizationId = :organizationId and r in :roles and m.deleted = false and u.deleted = false "
            + "and m.status = :status and u.status = com.pte.identity.domain.UserStatus.ACTIVE group by r")
    List<Object[]> countActiveByOrganizationAndRoles(@Param("organizationId") UUID organizationId,
            @Param("roles") Set<Role> roles, @Param("status") UserStatus status);

    /** Overview: students per organization across the whole platform in one grouped query. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("select m.organizationId, count(m.id) from OrganizationMember m join m.user u join m.roles r "
            + "where r = :role and m.deleted = false and u.deleted = false group by m.organizationId")
    List<Object[]> countByRoleGroupedByOrganization(@Param("role") Role role);

    /** Members of one organization carrying a role (notification fan-out). */
    @Query("select distinct m from OrganizationMember m join fetch m.user u join m.roles r "
            + "where m.organizationId = :organizationId and r = :role and m.deleted = false and u.deleted = false")
    List<OrganizationMember> findActiveByOrganizationAndRole(@Param("organizationId") UUID organizationId,
            @Param("role") Role role);

    /** Active members with a role across every organization. */
    @Query("select distinct m from OrganizationMember m join fetch m.user u join m.roles r "
            + "where r = :role and m.deleted = false and u.deleted = false and m.status = :status "
            + "and u.status = com.pte.identity.domain.UserStatus.ACTIVE")
    List<OrganizationMember> findActiveByRole(@Param("role") Role role, @Param("status") UserStatus status);

    /** Memberships of an account that carry a role (practice entitlement looks for STUDENT). */
    @Query("select m from OrganizationMember m join m.roles r where m.user.id = :userId and r = :role "
            + "and m.deleted = false")
    List<OrganizationMember> findByUserAndRole(@Param("userId") Long userId, @Param("role") Role role);

    @Query(value = "select m from OrganizationMember m join m.user u "
            + "where m.organizationId = :organizationId and m.deleted = false and u.deleted = false "
            + "and exists (select 1 from m.roles r where r in :staffRoles and (:role is null or r = :role)) "
            + "and (:status is null or m.status = :status) "
            + "and (:search = '' or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
            + "or lower(u.username) like concat('%', :search, '%'))",
            countQuery = "select count(m) from OrganizationMember m join m.user u "
                    + "where m.organizationId = :organizationId and m.deleted = false and u.deleted = false "
                    + "and exists (select 1 from m.roles r where r in :staffRoles "
                    + "and (:role is null or r = :role)) "
                    + "and (:status is null or m.status = :status) "
                    + "and (:search = '' or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
                    + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
                    + "or lower(u.username) like concat('%', :search, '%'))")
    Page<OrganizationMember> findPageForExamStaff(@Param("organizationId") UUID organizationId,
            @Param("staffRoles") Set<Role> staffRoles, @Param("role") Role role,
            @Param("status") UserStatus status, @Param("search") String search, Pageable pageable);
}
