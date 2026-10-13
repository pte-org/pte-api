package com.pte.billing.internal.repository;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByLicenseKey(String licenseKey);

    Optional<Subscription> findByLicenseKey(String licenseKey);

    Optional<Subscription> findByPublicId(UUID publicId);

    Optional<Subscription> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    Page<Subscription> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Subscription> findByDeletedFalseAndTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.publicId = :publicId AND s.tenantId = :tenantId AND s.deleted = false")
    Optional<Subscription> findWithLockByPublicIdAndTenantId(@Param("publicId") UUID publicId,
                                                               @Param("tenantId") UUID tenantId);

    Optional<Subscription> findByLicenseKeyAndTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThan(
            String licenseKey, UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    List<Subscription> findByTenantIdAndStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanOrderByCreatedAtDesc(
            UUID tenantId, SubscriptionStatus status, Instant startsAt, Instant expiresAt);

    /** Overview: the tenant's usable subscriptions at {@code now}, soonest expiry first (bounded by {@code pageable}). */
    @Query("SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.deleted = false "
            + "AND s.status = :status AND s.startsAt <= :now AND s.expiresAt > :now "
            + "ORDER BY s.expiresAt ASC, s.id ASC")
    List<Subscription> findUsableForOverview(@Param("tenantId") UUID tenantId,
            @Param("status") SubscriptionStatus status, @Param("now") Instant now, Pageable pageable);

    /** Overview (platform-wide): usable subscriptions at {@code now}. */
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.deleted = false AND s.status = :status "
            + "AND s.startsAt <= :now AND s.expiresAt > :now")
    long countUsable(@Param("status") SubscriptionStatus status, @Param("now") Instant now);

    /** Overview (platform-wide): usable subscriptions at {@code now} that expire at or before {@code until}. */
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.deleted = false AND s.status = :status "
            + "AND s.startsAt <= :now AND s.expiresAt > :now AND s.expiresAt <= :until")
    long countUsableExpiringBy(@Param("status") SubscriptionStatus status, @Param("now") Instant now,
            @Param("until") Instant until);

    /** Overview (platform-wide): subscriptions created (activated) in {@code [from, to)} grouped by activation source. */
    @Query("SELECT s.activationSource, COUNT(s) FROM Subscription s WHERE s.deleted = false "
            + "AND s.createdAt >= :from AND s.createdAt < :to GROUP BY s.activationSource")
    List<Object[]> countActivatedBySource(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview: usable subscriptions of several tenants in one query, soonest expiry first within a tenant. */
    @Query("SELECT s FROM Subscription s WHERE s.tenantId IN :tenantIds AND s.deleted = false "
            + "AND s.status = :status AND s.startsAt <= :now AND s.expiresAt > :now "
            + "ORDER BY s.tenantId ASC, s.expiresAt ASC, s.id ASC")
    List<Subscription> findUsableForTenants(@Param("tenantIds") Collection<UUID> tenantIds,
            @Param("status") SubscriptionStatus status, @Param("now") Instant now);

    List<Subscription> findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus status,
            Instant expiresAt);
}
