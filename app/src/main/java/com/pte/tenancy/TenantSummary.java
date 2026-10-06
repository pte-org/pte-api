package com.pte.tenancy;

import java.util.UUID;

/** Safe tenant identity projection exposed to other modules in batch form. */
public record TenantSummary(UUID publicId, String name, boolean deleted) {
}
