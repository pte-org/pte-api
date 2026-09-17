package com.pte.session.dto.event;

import java.util.List;
import java.util.UUID;

/** Published after a scheduled session is cancelled by subscription revocation. */
public record SessionCancelledEvent(UUID sessionPublicId, UUID tenantId, List<UUID> studentPublicIds) {

    public SessionCancelledEvent {
        studentPublicIds = studentPublicIds == null ? List.of() : List.copyOf(studentPublicIds);
    }
}
