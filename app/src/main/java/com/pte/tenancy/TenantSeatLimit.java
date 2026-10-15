package com.pte.tenancy;

import java.util.UUID;

/** Dashboard projection of one tenant's configured seat limit (no entity leaves tenancy). */
public record TenantSeatLimit(UUID publicId, String code, String name, int studentLimit) {
}
