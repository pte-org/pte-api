package com.pte.proctor.client.dto;

import java.util.UUID;

/** proctor's own local view of scheduling's response — no shared DTO across services. */
public record SchedulingProctorAssignmentResponse(UUID sessionPublicId, UUID tenantId) {
}
