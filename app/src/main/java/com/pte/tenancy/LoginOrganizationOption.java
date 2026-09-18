package com.pte.tenancy;

import java.util.UUID;

/** Public in-process tenant metadata used by identity's login disambiguation flow. */
public record LoginOrganizationOption(
        UUID tenantId,
        String tenantCode,
        String organizationName,
        String organizationType) {
}
