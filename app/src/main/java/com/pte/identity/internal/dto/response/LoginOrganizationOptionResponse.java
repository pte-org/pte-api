package com.pte.identity.internal.dto.response;

import java.util.UUID;

/** Safe organization choices shown after the credentials match one or more tenant accounts. */
public record LoginOrganizationOptionResponse(
        UUID tenantId,
        String tenantCode,
        String organizationName,
        String organizationType) {
}
