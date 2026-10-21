package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Platform confirmation payload for a live suspension-impact preview. */
public record SuspendTenantRequest(
        @PositiveOrZero Integer days,
        @NotBlank(message = TenancyConstants.TENANT_SUSPENSION_IMPACT_DIGEST_REQUIRED)
        String impactDigest) {
}
