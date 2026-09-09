package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ClassUpdated}. */
public record ClassUpdatedEvent(UUID classPublicId, UUID programPublicId, UUID tenantPublicId) {
}
