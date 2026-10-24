package com.pte.tenancy.domain;

import com.pte.shared.domain.BaseEntity;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A tenant of the platform: the customer organization that owns exams, classes, students and a subscription.
 * It starts as an application ({@link OrganizationStatus#PENDING}), becomes {@code ACTIVE} when a platform
 * reviewer approves it, and can later be suspended. Its {@code publicId} is the identifier other modules store
 * as {@code tenantId}.
 *
 * <p>Code, name and tax code must be unique among organizations that are not {@code REJECTED}; a rejection frees
 * them for a new application. That rule is a partial unique index (see {@code db/dev-seed/000-postgres-objects.sql}
 * and the final Flyway baseline) plus the {@code *AndStatusNot} repository checks.
 */
@Entity
@Table(name = "organizations", indexes = {
        @Index(name = "idx_organizations_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    /**
     * Chosen by the organization at application time; every student username its roster import generates is
     * prefixed with it. Set once at creation.
     */
    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String organizationType;

    /** Government tax identifier used by platform admins to verify the organization. */
    @Column(length = 64)
    private String taxCode;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrganizationStatus status = OrganizationStatus.PENDING;

    /** Null means an indefinite suspension that only an administrator can lift. */
    private Instant suspendedUntil;

    @Column(nullable = false)
    private String packageName = "starter";

    @Column(nullable = false)
    private int studentLimit;

    private String logoUrl;

    private String primaryColor;

    private String address;

    @Column(name = "province_code", length = 16)
    private String provinceCode;

    @Column(name = "province_name")
    private String provinceName;

    @Column(name = "ward_code", length = 16)
    private String wardCode;

    @Column(name = "ward_name")
    private String wardName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FacilityType facilityType = FacilityType.MAIN;

    /** Application contact, kept for the review and for the first HOST_ADMIN account. */
    private String contactEmail;

    private String contactPhone;

    /** {@code User.publicId} of the platform reviewer who approved or rejected the application. */
    private UUID reviewedBy;

    private Instant reviewedAt;

    @Column(length = 500)
    private String rejectReason;

    /** Guards the cached {@code packageName}/{@code studentLimit} counters against lost quota updates. */
    @Version
    private Long version;

    /**
     * Append-only history of seat grants, oldest first; the order column makes a new grant a single INSERT instead of
     * rewriting the whole history. The cached {@code studentLimit} is raised by the same call.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "organization_quota_grants", joinColumns = @JoinColumn(name = "organization_id"))
    @OrderColumn(name = "position")
    private List<QuotaGrant> quotaGrants = new ArrayList<>();

    /** Raises the student limit by {@code amount}, switches the package label and records the grant. */
    public QuotaGrant grantQuota(String packageName, int amount, UUID actorUserId, String note) {
        this.packageName = packageName;
        this.studentLimit += amount;
        QuotaGrant grant = new QuotaGrant(packageName, amount, actorUserId, note, Instant.now());
        quotaGrants.add(grant);
        return grant;
    }

    public void approve(UUID reviewerPublicId, Instant at) {
        requireStatus(OrganizationStatus.PENDING);
        this.status = OrganizationStatus.ACTIVE;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = at;
    }

    public void reject(UUID reviewerPublicId, Instant at, String reason) {
        requireStatus(OrganizationStatus.PENDING);
        this.status = OrganizationStatus.REJECTED;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = at;
        this.rejectReason = reason;
    }

    public void suspendUntil(Instant until) {
        if (status != OrganizationStatus.ACTIVE && status != OrganizationStatus.SUSPENDED) {
            throw new IllegalStateException("Only an approved organization can be suspended, was " + status);
        }
        this.status = OrganizationStatus.SUSPENDED;
        this.suspendedUntil = until;
    }

    public void reactivate() {
        requireStatus(OrganizationStatus.SUSPENDED);
        this.status = OrganizationStatus.ACTIVE;
        this.suspendedUntil = null;
    }

    public void updateBranding(String logoUrl, String primaryColor) {
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
    }

    public boolean isPending() {
        return status == OrganizationStatus.PENDING;
    }

    public boolean isRejected() {
        return status == OrganizationStatus.REJECTED;
    }

    private void requireStatus(OrganizationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected organization status " + expected + " but was " + status);
        }
    }
}
