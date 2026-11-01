package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe, short-lived snapshot used to confirm one revoke operation. */
public record LicenseRevokePreviewResponse(
        UUID publicId,
        UUID planId,
        String effectiveState,
        String impactCategory,
        UUID subscriptionPublicId,
        String subscriptionStatus,
        UUID tenantPublicId,
        int scheduledCount,
        int openCount,
        int closedCount,
        List<UUID> scheduledSessionPublicIds,
        Instant previewExpiresAt,
        String scopeDigest) {

    public LicenseRevokePreviewResponse {
        scheduledSessionPublicIds = scheduledSessionPublicIds == null
                ? List.of() : List.copyOf(scheduledSessionPublicIds);
    }
}
