package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code StudentAssignedToClass}. */
public record StudentAssignedToClassEvent(UUID membershipPublicId, UUID classPublicId, UUID studentPublicId,
                                           UUID tenantPublicId) {
}
