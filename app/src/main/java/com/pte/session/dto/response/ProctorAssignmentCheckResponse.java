package com.pte.session.dto.response;

import java.util.UUID;

/**
 * Confirms a proctor is assigned to a session, and hands back the session's
 * tenantId so the caller (proctoring) doesn't have to trust a client-supplied
 * tenant. Trusted application-call surface only.
 */
public record ProctorAssignmentCheckResponse(UUID sessionPublicId, UUID tenantId) {
}
