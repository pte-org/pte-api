package com.pte.tenancy;

import java.time.Instant;
import java.util.UUID;

/** Billing-backed read boundary used by tenancy's suspension preview. */
@FunctionalInterface
public interface TenantSuspensionImpactProvider {

    TenantSuspensionImpact preview(UUID tenantPublicId, Instant now, Instant suspendedUntil);
}
