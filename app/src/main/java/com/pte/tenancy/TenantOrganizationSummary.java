package com.pte.tenancy;

import java.util.UUID;

/** Safe batch tenant identity projection including the display organization type. */
public record TenantOrganizationSummary(UUID publicId, String name, String organizationType, boolean deleted) {
}
