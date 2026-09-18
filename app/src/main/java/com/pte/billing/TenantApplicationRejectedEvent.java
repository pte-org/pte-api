package com.pte.billing;

import java.util.UUID;

/** Published after a platform administrator rejects a tenant application. */
public record TenantApplicationRejectedEvent(
        UUID applicationPublicId,
        String organizationName,
        String requestedCode,
        String contactEmail,
        String rejectReason) {
}
