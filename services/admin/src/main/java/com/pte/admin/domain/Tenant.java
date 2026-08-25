package com.pte.admin.domain;

import com.pte.admin.domain.enums.TenantStatus;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An organization (host) onboarded onto the platform. This is the control-plane
 * record of a tenant's governance (status, package). Tenant <em>identity</em>
 * used at runtime lives in iam and travels in the JWT — data-plane services never
 * call admin for it (ADR-001).
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String organizationType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(nullable = false)
    private String packageName;

    @Column(nullable = false)
    private int studentLimit;

    private String logoUrl;

    private String primaryColor;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Organization> organizations = new ArrayList<>();

    /**
     * Optimistic lock guarding the cached {@code packageName}/{@code
     * studentLimit} counters below from a lost update when two quota grants
     * race for the same tenant (Phase 5).
     */
    @Version
    private Long version;

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = TenantStatus.ACTIVE;
    }

    public void updateBranding(String logoUrl, String primaryColor) {
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
    }

    public void addOrganization(Organization organization) {
        organization.setTenant(this);
        organizations.add(organization);
    }
}
