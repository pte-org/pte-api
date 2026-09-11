package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code OrganizationSuspended}. */
public record OrganizationSuspendedEvent(UUID organizationPublicId, UUID tenantPublicId) {
}
