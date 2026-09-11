package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ProgramCreated}. */
public record ProgramCreatedEvent(UUID programPublicId, UUID organizationPublicId, UUID tenantPublicId, String name) {
}
