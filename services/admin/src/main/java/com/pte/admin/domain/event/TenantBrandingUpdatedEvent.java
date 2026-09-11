package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code TenantBrandingUpdated}. */
public record TenantBrandingUpdatedEvent(UUID tenantPublicId, String logoUrl, String primaryColor) {
}
