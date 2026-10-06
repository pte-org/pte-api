package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Version precondition for activation and archive transitions. */
public record PlanTransitionRequest(
        @NotNull(message = BillingConstants.PLAN_VERSION_REQUIRED)
        @PositiveOrZero(message = BillingConstants.PLAN_VERSION_INVALID)
        Long expectedVersion) {
}
