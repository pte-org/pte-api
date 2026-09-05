package com.pte.scheduling.domain.event;

import java.util.UUID;

public record StudentUnenrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
