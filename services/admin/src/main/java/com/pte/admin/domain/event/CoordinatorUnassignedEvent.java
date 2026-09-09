package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code CoordinatorUnassigned}. */
public record CoordinatorUnassignedEvent(UUID assignmentPublicId, UUID programPublicId, UUID assigneePublicId,
                                          UUID tenantPublicId) {
}
