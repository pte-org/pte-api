package com.pte.billing.domain;

import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A bearer activation code issued for exactly one plan activation. */
@Entity
@Table(name = "license_codes", indexes = {
        @Index(name = "idx_license_codes_status_expiry", columnList = "status,code_expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_license_codes_code", columnNames = "code"))
@Getter
@NoArgsConstructor
public class LicenseCode extends BaseEntity {

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LicenseCodeStatus status = LicenseCodeStatus.ISSUED;

    @Column(nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    private Instant codeExpiresAt;

    private UUID redeemedByTenantId;

    private Instant redeemedAt;

    /** Public ID of the Subscription created by redemption; null for capacity plans. */
    @Column(unique = true)
    private UUID subscriptionId;

    @Column(length = 255)
    private String revokeReason;

    /** Organization an admin grant was issued for; null for a legacy bearer code. */
    @Column(updatable = false)
    private UUID boundTenantId;

    /** Start the admin chose for the granted subscription; null means it started at redemption. */
    @Column(updatable = false)
    private Instant requestedStartsAt;

    @Column(length = 64, updatable = false)
    private String contractReference;

    @Column(length = 500, updatable = false)
    private String note;

    private LicenseCode(String code, UUID planId, UUID issuedBy, Instant issuedAt, Instant codeExpiresAt) {
        this.code = code;
        this.planId = planId;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
        this.codeExpiresAt = codeExpiresAt;
    }

    public static LicenseCode issue(String code, UUID planId, UUID issuedBy, Instant issuedAt,
            Instant codeExpiresAt) {
        return new LicenseCode(code, planId, issuedBy, issuedAt, codeExpiresAt);
    }

    /** A code issued for one organization under a contract; the caller redeems it in the same transaction. */
    public static LicenseCode grant(String code, UUID planId, UUID issuedBy, Instant issuedAt, UUID boundTenantId,
            Instant requestedStartsAt, String contractReference, String note) {
        LicenseCode licenseCode = new LicenseCode(code, planId, issuedBy, issuedAt, null);
        licenseCode.boundTenantId = boundTenantId;
        licenseCode.requestedStartsAt = requestedStartsAt;
        licenseCode.contractReference = contractReference;
        licenseCode.note = note;
        return licenseCode;
    }

    public void markRedeemed(UUID tenantId, Instant redeemedAt) {
        if (status != LicenseCodeStatus.ISSUED) {
            throw new IllegalStateException("Only an issued license code can be redeemed");
        }
        this.status = LicenseCodeStatus.REDEEMED;
        this.redeemedByTenantId = tenantId;
        this.redeemedAt = redeemedAt;
    }

    public void linkSubscription(UUID subscriptionId) {
        if (status != LicenseCodeStatus.REDEEMED) {
            throw new IllegalStateException("Only a redeemed license code can link a subscription");
        }
        this.subscriptionId = subscriptionId;
    }

    public void revoke(String reason) {
        if (status != LicenseCodeStatus.ISSUED && status != LicenseCodeStatus.REDEEMED) {
            throw new IllegalStateException("Only an issued or redeemed license code can be revoked");
        }
        this.status = LicenseCodeStatus.REVOKED;
        this.revokeReason = reason;
    }

    public void expire() {
        if (status == LicenseCodeStatus.ISSUED) {
            status = LicenseCodeStatus.EXPIRED;
        }
    }
}
