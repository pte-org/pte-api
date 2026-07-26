package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code TenantOnboarded} — iam consumes it to seed the tenant registry. */
public record TenantOnboardedEvent(UUID tenantPublicId, String name, String organizationType) {
}
