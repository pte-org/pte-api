package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ClassStatusChanged}. */
public record ClassStatusChangedEvent(UUID classPublicId, UUID programPublicId, UUID tenantPublicId, String status) {
}
