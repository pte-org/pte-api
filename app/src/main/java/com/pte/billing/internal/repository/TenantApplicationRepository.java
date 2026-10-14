package com.pte.billing.internal.repository;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** Overview: non-deleted applications in the given review state. */
    long countByStatusAndDeletedFalse(TenantApplicationStatus status);

    /** Overview: applications received (created) in {@code [from, to)}. */
    @Query("select count(a) from TenantApplication a where a.deleted = false "
            + "and a.createdAt >= :from and a.createdAt < :to")
    long countReceivedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview: oldest applications in the given state first (waiting-time view), bounded by {@code pageable}. */
    @Query("select a from TenantApplication a where a.deleted = false and a.status = :status "
            + "order by a.createdAt asc, a.id asc")
    List<TenantApplication> findOldestByStatus(@Param("status") TenantApplicationStatus status, Pageable pageable);
}
