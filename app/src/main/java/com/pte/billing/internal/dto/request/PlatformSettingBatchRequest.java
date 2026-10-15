package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Atomic collection update used by the grouped Vendor Web settings form. */
public record PlatformSettingBatchRequest(
        @NotEmpty(message = BillingConstants.PLATFORM_SETTING_BATCH_REQUIRED)
        @Size(max = BillingConstants.MAX_PLATFORM_SETTING_BATCH_SIZE,
                message = BillingConstants.PLATFORM_SETTING_BATCH_MAX)
        List<@Valid PlatformSettingChangeRequest> settings) {
}
