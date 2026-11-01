package com.pte.billing.internal.dto.response.license;

/** The unmasked license key, returned only after a successful password re-entry. */
public record LicenseKeyResponse(String licenseKey) {
}
