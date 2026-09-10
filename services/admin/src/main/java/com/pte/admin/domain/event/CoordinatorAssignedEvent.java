package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code CoordinatorAssigned}. */
public record CoordinatorAssignedEvent(UUID assignmentPublicId, UUID programPublicId, UUID assigneePublicId,
                                        UUID tenantPublicId) {
}
