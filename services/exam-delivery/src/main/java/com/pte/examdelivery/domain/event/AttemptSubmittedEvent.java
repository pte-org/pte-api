package com.pte.examdelivery.domain.event;

import java.util.UUID;

public record AttemptSubmittedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
