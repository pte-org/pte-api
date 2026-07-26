package com.pte.notification.repository;

import com.pte.notification.domain.UserDirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectoryRepository extends JpaRepository<UserDirectoryEntry, Long> {

    Optional<UserDirectoryEntry> findByUserPublicId(UUID userPublicId);

    List<UserDirectoryEntry> findByTenantIdAndRolesContaining(UUID tenantId, String role);
}
