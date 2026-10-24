package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Account-level reads. Anything scoped to an organization (its members, their roles, counts) lives in
 * {@link OrganizationMemberRepository}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** The login key is unique across the platform, archived accounts included. */
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    /** Kept for the password-recovery flow only; login excludes archived users. */
    List<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

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
}
