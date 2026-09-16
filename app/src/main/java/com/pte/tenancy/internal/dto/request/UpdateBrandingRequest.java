package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.Pattern;

/** Admin sets a Tenant's white-label logo + primary color. Both optional â€” either may be cleared by sending null. */
public record UpdateBrandingRequest(
        String logoUrl,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = TenancyConstants.PRIMARY_COLOR_INVALID)
        String primaryColor) {
}
