package com.pte.billing.internal.dto.response;

import com.pte.billing.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID publicId,
        UUID tenantId,
        UUID planId,
        Long orderCode,
        BigDecimal amount,
        String currency,
        String status,
        String paymentLinkUrl,
        Instant paidAt,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getPublicId(),
                order.getTenantId(),
                order.getPlanId(),
                order.getOrderCode(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getPaymentLinkUrl(),
                order.getPaidAt(),
                order.getCreatedAt());
    }
}
