package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for changing one platform setting's value and description. */
public record PlatformSettingRequest(
        @NotBlank(message = BillingConstants.SETTING_VALUE_REQUIRED)
        @Size(max = 255, message = BillingConstants.SETTING_VALUE_MAX)
        String value,

        @Size(max = 255, message = BillingConstants.SETTING_DESCRIPTION_MAX)
        String description) {
}
