package com.pte.admin.dto.request;

import jakarta.validation.constraints.Pattern;

/** Admin sets a Tenant's white-label logo + primary color. Both optional — either may be cleared by sending null. */
public record UpdateBrandingRequest(
        String logoUrl,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must be a hex value like #1A2B3C")
        String primaryColor) {
}
