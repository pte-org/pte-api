package com.pte.billing.internal.repository;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantApplicationRepository extends JpaRepository<TenantApplication, Long> {

    Optional<TenantApplication> findByPublicId(UUID publicId);

    boolean existsByRequestedCodeAndStatus(String requestedCode, TenantApplicationStatus status);

    List<TenantApplication> findAllByOrderByCreatedAtDesc();
}
