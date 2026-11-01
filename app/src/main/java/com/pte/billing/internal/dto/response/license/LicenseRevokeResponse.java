package com.pte.billing.internal.dto.response;

import java.util.List;
import java.util.UUID;

/** Safe metadata and cancellation recap returned after a confirmed revoke. */
public record LicenseRevokeResponse(
        UUID publicId,
        String status,
        String impactCategory,
        UUID subscriptionPublicId,
        String subscriptionStatus,
        boolean subscriptionCancelled,
        int scheduledCancelledCount,
        int openPreservedCount,
        int closedPreservedCount,
        List<UUID> cancelledSessionPublicIds) {

    public LicenseRevokeResponse {
        cancelledSessionPublicIds = cancelledSessionPublicIds == null
                ? List.of() : List.copyOf(cancelledSessionPublicIds);
    }
}
