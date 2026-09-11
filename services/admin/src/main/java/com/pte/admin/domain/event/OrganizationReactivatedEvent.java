package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code OrganizationReactivated}. */
public record OrganizationReactivatedEvent(UUID organizationPublicId, UUID tenantPublicId) {
}
