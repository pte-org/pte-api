package com.pte.scheduling.domain.event;

import java.util.UUID;

public record ProctorAssignedEvent(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
}
