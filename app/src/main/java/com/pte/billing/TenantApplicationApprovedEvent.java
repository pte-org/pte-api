package com.pte.billing;

import java.util.UUID;

/** Published after a platform administrator approves a tenant application. */
public record TenantApplicationApprovedEvent(
        UUID applicationPublicId,
        UUID tenantPublicId,
        String organizationName,
        String tenantCode,
        String contactEmail,
        String hostAdminUsername) {
}
