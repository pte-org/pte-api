package com.pte.iam.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.iam.domain.enums.TenantRegistryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * iam's own read-optimized copy of tenant governance state (ADR-001: "tenant
 * registry lives in iam" — closes the gap left open since Phase 2). Populated
 * by the {@code TenantEventConsumer}, NEVER written by a human-facing endpoint
 * — admin remains the source of truth for tenant lifecycle, this is a
 * projection. Used to reject login/user-creation for a suspended tenant
 * without a runtime call to admin.
 */
@Entity
@Table(name = "tenant_registry")
@Getter
@Setter
@NoArgsConstructor
public class TenantRegistry extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID tenantPublicId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantRegistryStatus status = TenantRegistryStatus.ACTIVE;
}
