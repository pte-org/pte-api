package com.pte.admin.domain;

import com.pte.admin.domain.enums.TenantStatus;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }
}
