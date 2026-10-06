package com.pte.billing;

import java.util.UUID;

/** Published only after a pending order has been durably transitioned to EXPIRED. */
public record OrderExpiredEvent(
        UUID tenantPublicId,
        UUID orderPublicId,
        UUID planPublicId,
        Long orderCode) {
}
