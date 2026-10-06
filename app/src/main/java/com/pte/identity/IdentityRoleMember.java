package com.pte.identity;

import java.util.UUID;

/** Minimal public identity projection for role-based fan-out decisions. */
public record IdentityRoleMember(UUID userPublicId, UUID tenantId) {
}
