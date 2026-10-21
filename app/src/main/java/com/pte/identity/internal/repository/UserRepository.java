package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select (count(u) > 0) from User u where u.tenantId = :tenantId and u.deleted = false "
            + "and lower(trim(u.email)) = :email and u.publicId <> :excludedId")
    boolean existsCanonicalEmail(@Param("tenantId") UUID tenantId, @Param("email") String email,
            @Param("excludedId") UUID excludedId);

    List<User> findByUsername(String username);

    List<User> findByUsernameAndDeletedFalse(String username);

    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);

    Optional<User> findByUsernameAndTenantIdAndDeletedFalse(String username, UUID tenantId);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    /** Kept for the password-recovery flow only; login excludes archived users. */
    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByPublicIdAndDeletedFalse(UUID publicId);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId and u.deleted = false")
    Optional<User> findWithLockByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId")
    Optional<User> findWithLockByPublicIdIncludingDeleted(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId and u.tenantId = :tenantId "
            + "and u.deleted = false")
    Optional<User> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
            @Param("tenantId") UUID tenantId);

    List<User> findByPublicIdInAndTenantIdAndDeletedFalse(List<UUID> publicIds, UUID tenantId);

    List<User> findByPublicIdInAndDeletedFalse(List<UUID> publicIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId in :publicIds and u.tenantId = :tenantId "
            + "and u.deleted = false order by u.publicId")
    List<User> findWithLockByPublicIdsAndTenantId(@Param("publicIds") List<UUID> publicIds,
            @Param("tenantId") UUID tenantId);

    List<User> findByTenantId(UUID tenantId);

    @Query("select distinct u from User u join u.roles userRole "
            + "where u.tenantId is null and u.deleted = false and userRole in :platformRoles "
            + "and (:role is null or userRole = :role) "
            + "and (:status is null or u.status = :status) "
            + "and (:search = '' "
            + "or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.username, '')) like concat('%', :search, '%'))")
    Page<User> findPageForPlatformUsers(@Param("platformRoles") Set<Role> platformRoles,
            @Param("role") Role role, @Param("status") UserStatus status, @Param("search") String search,
            Pageable pageable);

    @Query("select distinct u from User u join u.roles userRole "
            + "where u.tenantId is null and userRole in :platformRoles "
            + "and (:includeArchived = true or u.deleted = false) "
            + "and (:role is null or userRole = :role) "
            + "and (:status is null or u.status = :status) "
            + "and (:search = '' "
            + "or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.username, '')) like concat('%', :search, '%'))")
    Page<User> findPageForPlatformUsersIncludingArchived(@Param("platformRoles") Set<Role> platformRoles,
            @Param("role") Role role, @Param("status") UserStatus status, @Param("search") String search,
            @Param("includeArchived") boolean includeArchived, Pageable pageable);

    @Query("select distinct u from User u join u.roles userRole "
            + "where u.tenantId is null and u.deleted = false and userRole in :platformRoles")
    List<User> findPlatformDirectory(@Param("platformRoles") Set<Role> platformRoles);

    @Query("""
            select distinct u
            from User u
            join u.roles userRole
            where u.tenantId = :tenantId
              and u.deleted = false
              and userRole in :staffRoles
              and (:role is null or userRole = :role)
              and (:status is null or u.status = :status)
              and (:search = ''
                   or lower(coalesce(u.fullName, '')) like concat('%', :search, '%')
                   or lower(coalesce(u.email, '')) like concat('%', :search, '%')
                   or lower(u.username) like concat('%', :search, '%'))
            """)
    Page<User> findPageForExamStaff(
            @Param("tenantId") UUID tenantId,
            @Param("staffRoles") Set<Role> staffRoles,
            @Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByEmail(String email);

    List<User> findByEmailIn(List<String> emails);

    List<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    List<User> findByTenantIdAndEmailIn(UUID tenantId, List<String> emails);

    @Query("select count(distinct u.id) from User u join u.roles role "
            + "where u.tenantId = :tenantId and role = :role and u.deleted = false")
    long countByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);

    /** Overview: grouped learner counts by status (non-deleted users carrying the role). */
    @Query("select u.status, count(distinct u.id) from User u join u.roles r "
            + "where u.tenantId = :tenantId and r = :role and u.deleted = false group by u.status")
    List<Object[]> countByTenantIdAndRoleGroupedByStatus(@Param("tenantId") UUID tenantId,
            @Param("role") Role role);

    /** Overview: new users with the role created in {@code [from, to)}. */
    @Query("select count(distinct u.id) from User u join u.roles r "
            + "where u.tenantId = :tenantId and r = :role and u.deleted = false "
            + "and u.createdAt >= :from and u.createdAt < :to")
    long countByTenantIdAndRoleCreatedBetween(@Param("tenantId") UUID tenantId, @Param("role") Role role,
            @Param("from") Instant from, @Param("to") Instant to);

    /** Overview: active, non-deleted users per role for a small role set (staff coverage). */
    @Query("select r, count(distinct u.id) from User u join u.roles r "
            + "where u.tenantId = :tenantId and r in :roles and u.deleted = false and u.status = :status "
            + "group by r")
    List<Object[]> countActiveByTenantIdAndRoles(@Param("tenantId") UUID tenantId,
            @Param("roles") Set<Role> roles, @Param("status") UserStatus status);

    /** Timeout hint (ms) for the platform-wide overview count. */
    String OVERVIEW_QUERY_TIMEOUT_MS = "2000";

    /**
     * Overview: students per tenant across the whole platform in one grouped query (the heaviest
     * admin count; bounded by a 2s timeout hint and cached by the caller). Tenant-less users are excluded.
     * {@code roles} is a mapped set and the join is filtered to one role, so each user matches once and a
     * plain {@code count} is exact as long as no duplicate (user_id, role) row exists; the schema does not
     * enforce that uniqueness (the application only ever inserts set elements). {@code count(distinct)}
     * would force a sort that spills to disk.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("select u.tenantId, count(u.id) from User u join u.roles r "
            + "where r = :role and u.deleted = false and u.tenantId is not null group by u.tenantId")
    List<Object[]> countByRoleGroupedByTenant(@Param("role") Role role);

    /** Host-admin fanout for notification (Phase 09) — every user in a tenant carrying the given role. */
    List<User> findByTenantIdAndRolesContaining(UUID tenantId, Role role);

    @Query("select distinct u from User u join u.roles userRole "
            + "where userRole = :role and u.deleted = false and u.status = :status")
    List<User> findActiveMembersByRole(@Param("role") Role role, @Param("status") UserStatus status);
}
