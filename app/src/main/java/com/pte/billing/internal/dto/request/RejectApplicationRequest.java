package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;

public record RejectApplicationRequest(
        @NotBlank(message = BillingConstants.REJECT_REASON_REQUIRED)
        String reason) {
}
