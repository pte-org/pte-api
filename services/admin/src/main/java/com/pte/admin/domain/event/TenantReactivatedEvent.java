package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code TenantReactivated}. */
public record TenantReactivatedEvent(UUID tenantPublicId) {
}
