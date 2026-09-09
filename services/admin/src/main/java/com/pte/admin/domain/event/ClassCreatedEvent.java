package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ClassCreated}. */
public record ClassCreatedEvent(UUID classPublicId, UUID programPublicId, UUID tenantPublicId, String name) {
}
