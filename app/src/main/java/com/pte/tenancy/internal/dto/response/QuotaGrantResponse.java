package com.pte.tenancy.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One seat grant in an organization's quota history. */
public record QuotaGrantResponse(
        UUID publicId,
        UUID tenantPublicId,
        String packageName,
        int amount,
        UUID actorUserId,
        String note,
        Instant createdAt) {
}
