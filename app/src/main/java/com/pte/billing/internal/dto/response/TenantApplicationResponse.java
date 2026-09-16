package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantApplicationResponse(
        UUID publicId,
        String orgName,
        String orgType,
        String requestedCode,
        String contactEmail,
        String contactPhone,
        String taxCode,
        String status,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectReason) {
}
