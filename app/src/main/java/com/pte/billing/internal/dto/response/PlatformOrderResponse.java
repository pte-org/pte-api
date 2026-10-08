package com.pte.billing.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Platform reporting projection; payment links stay on the tenant order contract. */
public record PlatformOrderResponse(
        UUID publicId,
        UUID tenantId,
        UUID planId,
        Long orderCode,
        BigDecimal amount,
        String currency,
        String status,
        Instant paidAt,
        Instant createdAt) {

    public static PlatformOrderResponse from(OrderResponse response) {
        return new PlatformOrderResponse(response.publicId(), response.tenantId(), response.planId(),
                response.orderCode(), response.amount(), response.currency(), response.status(),
                response.paidAt(), response.createdAt());
    }
}
