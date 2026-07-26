package com.pte.scheduling.domain.event;

import java.util.UUID;

/** Host command: "publish scored results for this session to students." */
public record PublishRequestedEvent(UUID sessionPublicId, UUID tenantId) {
}
