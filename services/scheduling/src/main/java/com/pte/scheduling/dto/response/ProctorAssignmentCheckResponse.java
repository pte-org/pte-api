package com.pte.scheduling.dto.response;

import java.util.UUID;

/**
 * Confirms a proctor is assigned to a session, and hands back the session's
 * tenantId so the caller (proctor service) doesn't have to trust a
 * client-supplied tenant. Internal service-to-service surface only.
 */
public record ProctorAssignmentCheckResponse(UUID sessionPublicId, UUID tenantId) {
}
