package com.pte.billing.domain;

import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An organization's self-submitted application to onboard as a tenant. Kept
 * separate from {@link com.pte.tenancy.domain.Tenant} on purpose — the
 * tenant does not exist until this is approved (ADR-006).
 */
@Entity
@Table(name = "tenant_applications")
@Getter
@Setter
@NoArgsConstructor
public class TenantApplication extends BaseEntity {

    @Column(nullable = false)
    private String orgName;

    @Column(nullable = false)
    private String orgType;

    /** Reserved the moment the application is submitted, not when approved — see {@code uq_application_code_active}. */
    @Column(nullable = false)
    private String requestedCode;

    @Column(nullable = false)
    private String contactEmail;

    @Column
    private String contactPhone;

    @Column
    private String taxCode;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantApplicationStatus status = TenantApplicationStatus.PENDING;

    /** {@code User.publicId} of the PLATFORM_ADMIN who approved/rejected this — null while PENDING. */
    @Column
    private UUID reviewedBy;

    @Column
    private Instant reviewedAt;

    @Column(length = 500)
    private String rejectReason;

    public void approve(UUID reviewerPublicId) {
        this.status = TenantApplicationStatus.APPROVED;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerPublicId, String reason) {
        this.status = TenantApplicationStatus.REJECTED;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = Instant.now();
        this.rejectReason = reason;
    }
}
