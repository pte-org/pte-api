package com.pte.billing.internal.dto.response.license;

/** Short-lived response for an explicitly authorized admin reveal. */
public record LicenseCodeRevealResponse(String code) {
    @Override
    public String toString() {
        return "LicenseCodeRevealResponse[code=<redacted>]";
    }
}
