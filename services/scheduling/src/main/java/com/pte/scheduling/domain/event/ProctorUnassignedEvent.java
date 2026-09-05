package com.pte.scheduling.domain.event;

import java.util.UUID;

public record ProctorUnassignedEvent(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
}
