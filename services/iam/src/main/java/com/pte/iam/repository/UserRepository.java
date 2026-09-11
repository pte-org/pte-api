package com.pte.iam.repository;

import com.pte.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
