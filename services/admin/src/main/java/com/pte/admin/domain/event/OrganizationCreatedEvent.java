package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code OrganizationCreated}. */
public record OrganizationCreatedEvent(UUID organizationPublicId, UUID tenantPublicId, String name) {
}
