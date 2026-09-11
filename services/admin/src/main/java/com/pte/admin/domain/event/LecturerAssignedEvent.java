package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code LecturerAssigned}. */
public record LecturerAssignedEvent(UUID assignmentPublicId, UUID classPublicId, UUID assigneePublicId,
                                     UUID tenantPublicId) {
}
