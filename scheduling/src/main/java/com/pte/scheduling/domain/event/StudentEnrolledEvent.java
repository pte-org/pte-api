package com.pte.scheduling.domain.event;

import java.util.UUID;

public record StudentEnrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
