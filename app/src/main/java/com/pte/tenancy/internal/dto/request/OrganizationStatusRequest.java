package com.pte.tenancy.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * {@code PATCH /organizations/{id}/status} body. {@code SUSPEND} needs the digest of the impact preview the
 * operator confirmed (and optionally the suspension length in days); {@code REACTIVATE} needs neither.
 */
public record OrganizationStatusRequest(
        @NotNull OrganizationStatusAction action,
        @PositiveOrZero Integer days,
        String impactDigest) {

    public SuspendTenantRequest asSuspension() {
        return new SuspendTenantRequest(days, impactDigest);
    }
}
