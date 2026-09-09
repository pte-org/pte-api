package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code StudentUnassignedFromClass}. */
public record StudentUnassignedFromClassEvent(UUID membershipPublicId, UUID classPublicId, UUID studentPublicId,
                                               UUID tenantPublicId) {
}
