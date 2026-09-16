package com.pte.billing.internal.repository;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByLicenseKey(String licenseKey);

    Optional<Subscription> findByLicenseKey(String licenseKey);

    Optional<Subscription> findByPublicId(UUID publicId);

    Optional<Subscription> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.publicId = :publicId AND s.tenantId = :tenantId")
    Optional<Subscription> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
                                                               @Param("tenantId") UUID tenantId);

    Optional<Subscription> findByLicenseKeyAndTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThan(
            String licenseKey, UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    List<Subscription> findByTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanOrderByCreatedAtDesc(
            UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    List<Subscription> findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus status,
            Instant expiresAt);
}
