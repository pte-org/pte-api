package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicationRequest(
        @NotBlank(message = BillingConstants.REJECT_REASON_REQUIRED)
        @Size(max = 500, message = BillingConstants.REJECT_REASON_MAX)
        String reason) {
}
