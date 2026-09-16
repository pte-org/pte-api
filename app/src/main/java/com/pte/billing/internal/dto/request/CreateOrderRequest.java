package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = BillingConstants.ORDER_PLAN_REQUIRED) UUID planId) {
}
