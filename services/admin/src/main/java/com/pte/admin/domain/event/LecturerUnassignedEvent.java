package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code LecturerUnassigned}. */
public record LecturerUnassignedEvent(UUID assignmentPublicId, UUID classPublicId, UUID assigneePublicId,
                                       UUID tenantPublicId) {
}
