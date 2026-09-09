package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ProgramUpdated}. */
public record ProgramUpdatedEvent(UUID programPublicId, UUID organizationPublicId, UUID tenantPublicId) {
}
