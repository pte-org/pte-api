package com.pte.tenancy;

import java.time.Instant;
import java.util.UUID;

/** Dashboard row of a recently created tenant; {@code status} is the enum name (no domain enum leaks). */
public record RecentTenantView(UUID publicId, String code, String name, String organizationType, String status,
        int studentLimit, Instant createdAt) {
}
