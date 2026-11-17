package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
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

/** Account reads: by key, platform directory, and organization-scoped lookups, locks and counts. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Timeout hint (ms) for the platform-wide overview count. */
    String OVERVIEW_QUERY_TIMEOUT_MS = "2000";

    /** The login key is unique across the platform, archived accounts included. */
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    boolean existsByEmailIgnoreCaseAndDeletedFalseAndIdNot(String email, Long id);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByPublicIdAndDeletedFalse(UUID publicId);

    Optional<User> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId and u.deleted = false")
    Optional<User> findWithLockByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId")
    Optional<User> findWithLockByPublicIdIncludingDeleted(@Param("publicId") UUID publicId);

    @Query("select distinct u from User u join u.roles userRole "
            + "where u.deleted = false and userRole in :platformRoles "
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
            + "where userRole in :platformRoles "
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
            + "where u.deleted = false and userRole in :platformRoles")
    List<User> findPlatformDirectory(@Param("platformRoles") Set<Role> platformRoles);

    // ---- Organization-scoped reads. Every one carries the organization predicate explicitly: an account of
    // another organization (or a platform account) is never returned, so ids never leak across organizations.

    @Query("select u from User u where u.publicId = :userPublicId and u.organizationId = :organizationId "
            + "and u.deleted = false")
    Optional<User> findActiveByUserAndOrganization(@Param("userPublicId") UUID userPublicId,
            @Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :userPublicId and u.organizationId = :organizationId "
            + "and u.deleted = false")
    Optional<User> findWithLockByUserAndOrganization(@Param("userPublicId") UUID userPublicId,
            @Param("organizationId") UUID organizationId);

    @Query("select u from User u where u.publicId in :userPublicIds and u.organizationId = :organizationId "
            + "and u.deleted = false")
    List<User> findActiveByUsersAndOrganization(@Param("userPublicIds") Collection<UUID> userPublicIds,
            @Param("organizationId") UUID organizationId);

    /** Locks are taken in public-id order so concurrent enrollment cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId in :userPublicIds and u.organizationId = :organizationId "
            + "and u.deleted = false order by u.publicId")
    List<User> findWithLockByUsersAndOrganization(@Param("userPublicIds") Collection<UUID> userPublicIds,
            @Param("organizationId") UUID organizationId);

    @Query("select u from User u where u.organizationId = :organizationId and u.deleted = false")
    List<User> findActiveByOrganization(@Param("organizationId") UUID organizationId);

    @Query("select u from User u where u.organizationId = :organizationId and u.deleted = false "
            + "and lower(trim(u.email)) in :emails")
    List<User> findActiveByOrganizationAndEmails(@Param("organizationId") UUID organizationId,
            @Param("emails") Collection<String> lowerCaseEmails);

    /** Whether another live account of the organization already uses this e-mail (case-insensitive). */
    @Query("select (count(u) > 0) from User u where u.organizationId = :organizationId and u.deleted = false "
            + "and lower(trim(u.email)) = :email and u.publicId <> :excludedUserPublicId")
    boolean existsCanonicalEmail(@Param("organizationId") UUID organizationId, @Param("email") String email,
            @Param("excludedUserPublicId") UUID excludedUserPublicId);

    @Query("select count(distinct u.id) from User u join u.roles r "
            + "where u.organizationId = :organizationId and r = :role and u.deleted = false")
    long countByOrganizationIdAndRole(@Param("organizationId") UUID organizationId, @Param("role") Role role);

    /** Overview: grouped learner counts by account status. */
    @Query("select u.status, count(distinct u.id) from User u join u.roles r "
            + "where u.organizationId = :organizationId and r = :role and u.deleted = false group by u.status")
    List<Object[]> countByOrganizationAndRoleGroupedByStatus(@Param("organizationId") UUID organizationId,
            @Param("role") Role role);

    /** Overview: new accounts with the role created in {@code [from, to)}. */
    @Query("select count(distinct u.id) from User u join u.roles r "
            + "where u.organizationId = :organizationId and r = :role and u.deleted = false "
            + "and u.createdAt >= :from and u.createdAt < :to")
    long countByOrganizationAndRoleCreatedBetween(@Param("organizationId") UUID organizationId,
            @Param("role") Role role, @Param("from") Instant from, @Param("to") Instant to);

    /** Overview: active accounts per role for a small role set (staff coverage). */
    @Query("select r, count(distinct u.id) from User u join u.roles r "
            + "where u.organizationId = :organizationId and r in :roles and u.deleted = false "
            + "and u.status = :status group by r")
    List<Object[]> countActiveByOrganizationAndRoles(@Param("organizationId") UUID organizationId,
            @Param("roles") Set<Role> roles, @Param("status") UserStatus status);

    /** Overview: students per organization across the whole platform in one grouped query. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = OVERVIEW_QUERY_TIMEOUT_MS))
    @Query("select u.organizationId, count(u.id) from User u join u.roles r "
            + "where r = :role and u.deleted = false and u.organizationId is not null group by u.organizationId")
    List<Object[]> countByRoleGroupedByOrganization(@Param("role") Role role);

    /** Accounts of one organization carrying a role (notification fan-out). */
    @Query("select distinct u from User u join u.roles r "
            + "where u.organizationId = :organizationId and r = :role and u.deleted = false")
    List<User> findActiveByOrganizationAndRole(@Param("organizationId") UUID organizationId,
            @Param("role") Role role);

    /** Active organization accounts with a role across every organization. */
    @Query("select distinct u from User u join u.roles r "
            + "where r = :role and u.deleted = false and u.organizationId is not null and u.status = :status")
    List<User> findActiveByRole(@Param("role") Role role, @Param("status") UserStatus status);

    @Query(value = "select u from User u "
            + "where u.organizationId = :organizationId and u.deleted = false "
            + "and exists (select 1 from u.roles r where r in :staffRoles and (:role is null or r = :role)) "
            + "and (:status is null or u.status = :status) "
            + "and (:search = '' or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
            + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
            + "or lower(u.username) like concat('%', :search, '%'))",
            countQuery = "select count(u) from User u "
                    + "where u.organizationId = :organizationId and u.deleted = false "
                    + "and exists (select 1 from u.roles r where r in :staffRoles "
                    + "and (:role is null or r = :role)) "
                    + "and (:status is null or u.status = :status) "
                    + "and (:search = '' or lower(coalesce(u.fullName, '')) like concat('%', :search, '%') "
                    + "or lower(coalesce(u.email, '')) like concat('%', :search, '%') "
                    + "or lower(u.username) like concat('%', :search, '%'))")
    Page<User> findPageForExamStaff(@Param("organizationId") UUID organizationId,
            @Param("staffRoles") Set<Role> staffRoles, @Param("role") Role role,
            @Param("status") UserStatus status, @Param("search") String search, Pageable pageable);
}
