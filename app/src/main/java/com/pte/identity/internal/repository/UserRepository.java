package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByUsername(String username);

    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    /** Kept for the password-recovery flow only — login itself uses {@link #findByUsername}. */
    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId and u.deleted = false")
    Optional<User> findWithLockByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId and u.tenantId = :tenantId "
            + "and u.deleted = false")
    Optional<User> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
            @Param("tenantId") UUID tenantId);

    List<User> findByPublicIdInAndTenantIdAndDeletedFalse(List<UUID> publicIds, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId in :publicIds and u.tenantId = :tenantId "
            + "and u.deleted = false order by u.id")
    List<User> findWithLockByPublicIdsAndTenantId(@Param("publicIds") List<UUID> publicIds,
            @Param("tenantId") UUID tenantId);

    List<User> findByTenantId(UUID tenantId);

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

    List<User> findByTenantIdAndEmailIn(UUID tenantId, List<String> emails);

    @Query("select count(distinct u.id) from User u join u.roles role "
            + "where u.tenantId = :tenantId and role = :role and u.deleted = false")
    long countByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);

    /** Host-admin fanout for notification (Phase 09) — every user in a tenant carrying the given role. */
    List<User> findByTenantIdAndRolesContaining(UUID tenantId, Role role);
}
