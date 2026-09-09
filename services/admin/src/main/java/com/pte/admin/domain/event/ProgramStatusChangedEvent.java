package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code ProgramStatusChanged}. */
public record ProgramStatusChangedEvent(UUID programPublicId, UUID organizationPublicId, UUID tenantPublicId,
                                         String status) {
}
