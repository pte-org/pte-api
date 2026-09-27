package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Shared create/update payload for the two mutually exclusive plan families. */
public record PlanRequest(
        @NotBlank(message = BillingConstants.PLAN_NAME_REQUIRED)
        String name,

        @Size(max = 255, message = BillingConstants.PLAN_DESCRIPTION_MAX)
        String description,

        @NotBlank(message = BillingConstants.PLAN_TYPE_REQUIRED)
        String type,

        @NotNull(message = BillingConstants.PLAN_PRICE_REQUIRED)
        @DecimalMin(value = "0.0", message = BillingConstants.PLAN_PRICE_NON_NEGATIVE)
        @Digits(integer = 17, fraction = 2, message = BillingConstants.PLAN_PRICE_PRECISION_INVALID)
        BigDecimal price,

        @NotBlank(message = BillingConstants.PLAN_CURRENCY_REQUIRED)
        @Size(min = 3, max = 3, message = BillingConstants.PLAN_CURRENCY_INVALID)
        String currency,

        Integer durationDays,
        Integer maxStudentsPerSession,
        Integer extraStudentSlots) {
}
