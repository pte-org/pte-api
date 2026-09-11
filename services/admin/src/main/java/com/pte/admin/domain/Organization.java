package com.pte.admin.domain;

import com.pte.admin.domain.enums.FacilityType;
import com.pte.admin.domain.enums.OrganizationStatus;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A branch/facility under a {@link Tenant} (Host). Has its own
 * {@code publicId} because it is referenced independently (e.g. from a
 * future scheduling/session-location feature), not only nested under Tenant.
 */
@Entity
@Table(name = "organizations", indexes = {@Index(name = "idx_organizations_tenant", columnList = "tenant_id")})
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FacilityType facilityType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    public void suspend() {
        this.status = OrganizationStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = OrganizationStatus.ACTIVE;
    }
}
