package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** A platform admin grants additional student seats to an organization. */
public record GrantQuotaRequest(
        @NotBlank(message = TenancyConstants.PACKAGE_NAME_REQUIRED)
        String packageName,

        @NotNull(message = TenancyConstants.AMOUNT_REQUIRED)
        @Positive(message = TenancyConstants.AMOUNT_POSITIVE)
        Integer amount,

        String note) {
}
