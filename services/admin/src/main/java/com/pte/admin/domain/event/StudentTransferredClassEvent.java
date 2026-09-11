package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code StudentTransferredClass}. */
public record StudentTransferredClassEvent(UUID membershipPublicId, UUID fromClassPublicId, UUID toClassPublicId,
                                            UUID studentPublicId, UUID tenantPublicId) {
}
