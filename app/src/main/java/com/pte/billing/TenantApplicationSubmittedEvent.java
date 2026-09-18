package com.pte.billing;

import java.util.UUID;

/** Published after a tenant application is persisted successfully. */
public record TenantApplicationSubmittedEvent(
        UUID applicationPublicId,
        String organizationName,
        String requestedCode,
        String contactEmail) {
}
