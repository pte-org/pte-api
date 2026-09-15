package com.pte.iam.repository;

import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    List<User> findByTenantId(UUID tenantId);

    boolean existsByEmail(String email);

    List<User> findByEmailIn(List<String> emails);

    /** Cursor-paged, safe-field source for Admin's rebuildable student projection. */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles role "
            + "WHERE role = :studentRole AND u.tenantId IS NOT NULL "
            + "AND (:tenantId IS NULL OR u.tenantId = :tenantId) "
            + "AND (u.createdAt > :cursorTime OR (u.createdAt = :cursorTime AND u.publicId > :cursorId)) "
            + "ORDER BY u.createdAt ASC, u.publicId ASC")
    List<User> findStudentsForExport(@Param("tenantId") UUID tenantId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            @Param("studentRole") Role studentRole,
            Pageable pageable);
}
