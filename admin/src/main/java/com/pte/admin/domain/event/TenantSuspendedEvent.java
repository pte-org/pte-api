package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code TenantSuspended}. */
public record TenantSuspendedEvent(UUID tenantPublicId) {
}
