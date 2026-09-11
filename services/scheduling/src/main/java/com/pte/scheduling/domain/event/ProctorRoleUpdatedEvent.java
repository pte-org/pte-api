package com.pte.scheduling.domain.event;

import com.pte.scheduling.domain.enums.ProctorRole;

import java.util.UUID;

public record ProctorRoleUpdatedEvent(UUID sessionPublicId, UUID proctorPublicId, ProctorRole role, UUID tenantId) {
}
