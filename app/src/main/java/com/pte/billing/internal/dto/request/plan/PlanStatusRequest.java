package com.pte.billing.internal.dto.request.plan;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** {@code PATCH /plans/{id}/status} body; the version precondition protects against stale decisions. */
public record PlanStatusRequest(
        @NotNull PlanStatusAction action,
        @NotNull(message = BillingConstants.PLAN_VERSION_REQUIRED)
        @PositiveOrZero(message = BillingConstants.PLAN_VERSION_INVALID)
        Long expectedVersion) {

    public PlanTransitionRequest asTransition() {
        return new PlanTransitionRequest(expectedVersion);
    }
}
