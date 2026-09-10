package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ClassArchived}. */
public record ClassArchivedEvent(UUID classPublicId, UUID programPublicId, UUID tenantPublicId) {
}
