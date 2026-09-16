package com.pte.billing.domain;

import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One purchased EXAM_PACKAGE instance. Tenant and plan references are scalar
 * public IDs by design: billing does not create cross-module JPA joins.
 *
 * <p>The per-session cap is a contract snapshot. It must never be re-read from
 * the mutable Plan after this row is activated.
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_tenant_status", columnList = "tenant_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false, unique = true, length = 32, updatable = false)
    private String licenseKey;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Integer maxStudentsPerSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ActivationSource activationSource;

    public boolean isUsableAt(Instant now) {
        return !isDeleted()
                && status == SubscriptionStatus.ACTIVE
                && !startsAt.isAfter(now)
                && expiresAt.isAfter(now);
    }

    public void expire() {
        if (status == SubscriptionStatus.ACTIVE) {
            status = SubscriptionStatus.EXPIRED;
        }
    }

    public void cancel() {
        if (status == SubscriptionStatus.ACTIVE) {
            status = SubscriptionStatus.CANCELLED;
        }
    }
}
