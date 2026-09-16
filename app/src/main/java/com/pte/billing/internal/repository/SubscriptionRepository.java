package com.pte.billing.internal.repository;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByLicenseKey(String licenseKey);

    Optional<Subscription> findByLicenseKeyAndTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThan(
            String licenseKey, UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    List<Subscription> findByTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanOrderByCreatedAtDesc(
            UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    List<Subscription> findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus status,
            Instant expiresAt);
}
