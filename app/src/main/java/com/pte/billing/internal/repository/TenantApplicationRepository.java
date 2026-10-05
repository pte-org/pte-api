package com.pte.billing.internal.repository;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantApplicationRepository extends JpaRepository<TenantApplication, Long> {

    Optional<TenantApplication> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from TenantApplication application where application.publicId = :publicId")
    Optional<TenantApplication> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    boolean existsByRequestedCodeAndStatus(String requestedCode, TenantApplicationStatus status);

    boolean existsByOrgNameAndStatus(String orgName, TenantApplicationStatus status);

    List<TenantApplication> findAllByOrderByCreatedAtDesc();
}
