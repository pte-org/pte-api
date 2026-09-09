package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ProgramArchived}. */
public record ProgramArchivedEvent(UUID programPublicId, UUID organizationPublicId, UUID tenantPublicId) {
}
