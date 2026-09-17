package com.pte.billing.internal.dto.response;

import java.util.UUID;

/** {@code hostAdminPassword} is the ONLY time this password is ever shown — not stored in the clear, not re-fetchable. */
public record ApproveApplicationResponse(
        UUID tenantPublicId,
        String tenantCode,
        UUID hostAdminPublicId,
        String hostAdminUsername,
        String hostAdminPassword) {
}
