package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One typed value inside an atomic platform-setting batch update. */
public record PlatformSettingChangeRequest(
        @NotBlank(message = BillingConstants.SETTING_KEY_REQUIRED)
        @Size(max = 100, message = BillingConstants.SETTING_KEY_MAX)
        String key,

        @NotBlank(message = BillingConstants.SETTING_VALUE_REQUIRED)
        @Size(max = 255, message = BillingConstants.SETTING_VALUE_MAX)
        String value) {
}
