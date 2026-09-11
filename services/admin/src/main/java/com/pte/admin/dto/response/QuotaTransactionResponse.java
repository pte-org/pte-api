package com.pte.admin.dto.response;

import java.time.Instant;
import java.util.UUID;

public record QuotaTransactionResponse(
        UUID publicId,
        UUID tenantPublicId,
        String packageName,
        int amount,
        String actionType,
        UUID actorUserId,
        String note,
        Instant createdAt) {
}
