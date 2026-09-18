package com.pte.billing;

import java.util.UUID;

/**
 * Published after a platform administrator approves a tenant application.
 * The generated password is transient notification data and is never returned
 * by the approval API or retained in notification history.
 */
public record TenantApplicationApprovedEvent(
        UUID applicationPublicId,
        UUID tenantPublicId,
        String organizationName,
        String tenantCode,
        String contactEmail,
        String hostAdminUsername,
        String hostAdminPassword) {
}
