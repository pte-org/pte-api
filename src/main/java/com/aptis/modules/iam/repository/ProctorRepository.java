package com.aptis.modules.iam.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.iam.domain.Proctor;

public interface ProctorRepository extends JpaRepository<Proctor, Long> {
    Optional<Proctor> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Proctor> findByOrganizationId(Long organizationId);

    Optional<Proctor> findByIdAndOrganizationId(Long id, Long organizationId);
}
